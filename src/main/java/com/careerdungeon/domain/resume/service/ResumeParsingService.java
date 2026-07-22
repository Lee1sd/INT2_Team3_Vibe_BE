package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.event.ResumeUploadedEvent;
import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import com.careerdungeon.domain.resume.parser.ResumeTextExtractor;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// 이력서 업로드 신호(ResumeUploadedEvent)를 받아 파일 형식별로 텍스트 추출
@Service
public class ResumeParsingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingService.class);
    private static final long CACHE_TTL_DAYS = 30;

    private final ResumeRepository resumeRepository;
    private final ResumeTextExtractor resumeTextExtractor;
    private final ResumePiiMaskingService piiMaskingService;
    private final ResumeFileCleanupService resumeFileCleanupService;
    private final ResumeFileStorage resumeFileStorage;
    private final ResumeParsingPersistenceService parsingPersistenceService;

    public ResumeParsingService(ResumeRepository resumeRepository,
                                ResumeTextExtractor resumeTextExtractor,
                                ResumePiiMaskingService piiMaskingService,
                                ResumeFileCleanupService resumeFileCleanupService,
                                ResumeFileStorage resumeFileStorage,
                                ResumeParsingPersistenceService parsingPersistenceService) {
        this.resumeRepository = resumeRepository;
        this.resumeTextExtractor = resumeTextExtractor;
        this.piiMaskingService = piiMaskingService;
        this.resumeFileCleanupService = resumeFileCleanupService;
        this.resumeFileStorage = resumeFileStorage;
        this.parsingPersistenceService = parsingPersistenceService;
    }

    // 업로드 DB 트랜잭션이 커밋된 뒤에만 실행한다.
    @Async
    // S3 다운로드/삭제는 트랜잭션 밖에서 수행하고, 결과 DB 갱신만 별도 서비스에서 처리한다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleResumeUploaded(ResumeUploadedEvent event) {
        parse(event.resumeId());
    }

    void parse(Long resumeId) {
        Optional<Resume> found = resumeRepository.findByIdAndDeletedAtIsNull(resumeId);
        if (found.isEmpty()) {
            log.warn("파싱 대상 Resume을 찾을 수 없음 (resumeId={})", resumeId);
            return;
        }
        Resume resume = found.get();
        String s3Key = resume.getS3Key();

        try {
            byte[] originalBytes = resumeFileStorage.download(s3Key);
            String extractedText = piiMaskingService.mask(resumeTextExtractor.extract(s3Key, originalBytes));
            Instant cacheExpiresAt = resume.getLastUploadedAt().plus(CACHE_TTL_DAYS, ChronoUnit.DAYS);
            parsingPersistenceService.markDoneIfActive(resumeId, extractedText, cacheExpiresAt);
        } catch (ResumeParsingFailedException e) {
            // 비동기 리스너라 이미 RS-001 응답이 나간 뒤다 — 컨트롤러로 던져봐야 받을 사람이 없으므로
            // 여기서 끝내고 상태만 FAILED로 남긴다. 사용자는 RS-002 폴링으로 확인한다.
            log.warn("이력서 파싱 실패 (resumeId={})", resumeId, e);
            parsingPersistenceService.markFailedIfActive(resumeId);
        } catch (Exception e) {
            // 예상 못한 예외(NPE 등)까지 여기서 잡지 않으면 markFailed()가 호출되지 않아
            // parseStatus가 PROCESSING에 영구히 멈춘다 — RS-002 폴링이 끝나지 않는 문제 방지.
            log.error("이력서 파싱 중 예상치 못한 예외 발생 (resumeId={})", resumeId, e);
            parsingPersistenceService.markFailedIfActive(resumeId);
        } finally {
            // privacy-policy.md "파일 처리 정책" §2 — 파싱 성공/실패와 무관하게 원본 파일을 즉시 삭제한다.
            deleteOriginalFile(resumeId, s3Key);
        }
    }

    private void deleteOriginalFile(Long resumeId, String s3Key) {
        if (s3Key == null) {
            return;
        }
        try {
            resumeFileStorage.delete(s3Key);
        } catch (RuntimeException e) {
            resumeFileCleanupService.enqueue(resumeId, s3Key);
            log.warn("이력서 원본 파일 삭제 실패 (resumeId={}, keyId={}, errorType={})",
                    resumeId, Integer.toUnsignedString(s3Key.hashCode(), 16),
                    e.getClass().getSimpleName());
        }
    }
}
