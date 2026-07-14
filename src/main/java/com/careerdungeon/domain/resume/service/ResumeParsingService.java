package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.event.ResumeUploadedEvent;
import com.careerdungeon.domain.resume.exception.ResumeParsingFailedException;
import com.careerdungeon.domain.resume.parser.ResumeTextExtractor;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

// 이력서 업로드 신호(ResumeUploadedEvent) 받아서 실제로 PDF 텍스트 뽑아내기
@Service
public class ResumeParsingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParsingService.class);
    private static final long CACHE_TTL_DAYS = 30;

    private final ResumeRepository resumeRepository;
    private final ResumeTextExtractor resumeTextExtractor;

    public ResumeParsingService(ResumeRepository resumeRepository, ResumeTextExtractor resumeTextExtractor) {
        this.resumeRepository = resumeRepository;
        this.resumeTextExtractor = resumeTextExtractor;
    }

    // upload() 트랜잭션이 커밋된 뒤에만 실행된다 — 커밋 전에 돌면 이 스레드가
    // 아직 안 보이는 Resume 행을 조회하게 되는 레이스가 생기기 때문 (AFTER_COMMIT 필수).
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleResumeUploaded(ResumeUploadedEvent event) {
        parse(event.resumeId());
    }

    @Transactional
    void parse(Long resumeId) {
        Optional<Resume> found = resumeRepository.findById(resumeId);
        if (found.isEmpty()) {
            log.warn("파싱 대상 Resume을 찾을 수 없음 (resumeId={})", resumeId);
            return;
        }
        Resume resume = found.get();

        try {
            // TODO: 추출 성공 후 이메일 등 PII 마스킹 적용 후 저장 (FR-11, privacy-policy.md)
            String extractedText = resumeTextExtractor.extract(resume.getS3Key());
            Instant cacheExpiresAt = Instant.now().plus(CACHE_TTL_DAYS, ChronoUnit.DAYS);
            resume.markDone(extractedText, cacheExpiresAt);
        } catch (ResumeParsingFailedException e) {
            // 비동기 리스너라 이미 RS-001 응답이 나간 뒤다 — 컨트롤러로 던져봐야 받을 사람이 없으므로
            // 여기서 끝내고 상태만 FAILED로 남긴다. 사용자는 RS-002 폴링으로 확인한다.
            log.warn("이력서 파싱 실패 (resumeId={})", resumeId, e);
            resume.markFailed();
        } finally {
            // TODO: 원본 파일 즉시 삭제 (privacy-policy.md "파일 처리 정책" §2 — 성공/실패 무관하게 보장)
        }
    }
}
