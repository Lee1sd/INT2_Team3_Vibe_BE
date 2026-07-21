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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public ResumeParsingService(ResumeRepository resumeRepository, ResumeTextExtractor resumeTextExtractor) {
        this.resumeRepository = resumeRepository;
        this.resumeTextExtractor = resumeTextExtractor;
    }

    // upload() 트랜잭션이 커밋된 뒤에만 실행된다 — 커밋 전에 돌면 이 스레드가
    // 아직 안 보이는 Resume 행을 조회하게 되는 레이스가 생기기 때문 (AFTER_COMMIT 필수).
    @Async
    // AFTER_COMMIT 시점엔 원본 트랜잭션이 이미 끝나 있어, 여기 @Transactional을 걸 때
    // REQUIRES_NEW가 아니면 RestrictedTransactionalEventListenerFactory가 컨텍스트
    // 기동 시점에 IllegalStateException을 던진다(Spring 강제 제약).
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleResumeUploaded(ResumeUploadedEvent event) {
        parse(event.resumeId());
    }

    // parse()는 handleResumeUploaded()의 self-invocation으로만 호출된다 — 같은 클래스 내부
    // 호출은 프록시를 안 거치므로 여기 @Transactional을 붙여봐야 무시된다. 트랜잭션은
    // 반드시 프록시를 타는 handleResumeUploaded()에 걸어야 한다.
    void parse(Long resumeId) {
        Optional<Resume> found = resumeRepository.findByIdAndDeletedAtIsNull(resumeId);
        if (found.isEmpty()) {
            log.warn("파싱 대상 Resume을 찾을 수 없음 (resumeId={})", resumeId);
            return;
        }
        Resume resume = found.get();
        String s3Key = resume.getS3Key();

        try {
            // TODO: 추출 성공 후 이메일 등 PII 마스킹 적용 후 저장 (FR-11, privacy-policy.md)
            String extractedText = resumeTextExtractor.extract(s3Key);
            Instant cacheExpiresAt = resume.getLastUploadedAt().plus(CACHE_TTL_DAYS, ChronoUnit.DAYS);
            resumeRepository.updateParseResultIfActive(
                    resumeId, extractedText, ParseStatus.DONE, cacheExpiresAt);
        } catch (ResumeParsingFailedException e) {
            // 비동기 리스너라 이미 RS-001 응답이 나간 뒤다 — 컨트롤러로 던져봐야 받을 사람이 없으므로
            // 여기서 끝내고 상태만 FAILED로 남긴다. 사용자는 RS-002 폴링으로 확인한다.
            log.warn("이력서 파싱 실패 (resumeId={})", resumeId, e);
            resumeRepository.updateParseResultIfActive(resumeId, null, ParseStatus.FAILED, null);
        } catch (Exception e) {
            // 예상 못한 예외(NPE 등)까지 여기서 잡지 않으면 markFailed()가 호출되지 않아
            // parseStatus가 PROCESSING에 영구히 멈춘다 — RS-002 폴링이 끝나지 않는 문제 방지.
            log.error("이력서 파싱 중 예상치 못한 예외 발생 (resumeId={})", resumeId, e);
            resumeRepository.updateParseResultIfActive(resumeId, null, ParseStatus.FAILED, null);
        } finally {
            // privacy-policy.md "파일 처리 정책" §2 — 파싱 성공/실패와 무관하게 원본 파일을 즉시 삭제한다.
            deleteOriginalFile(resumeId, s3Key);
        }
    }

    // TODO(임시 구현): S3Client 연동 시 로컬 파일 삭제 대신 실제 DeleteObject 호출로 교체한다.
    private void deleteOriginalFile(Long resumeId, String s3Key) {
        if (s3Key == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(s3Key));
        } catch (IOException e) {
            // 삭제 실패는 파싱 결과(markDone/markFailed)에 영향을 주지 않는다 — 로그만 남기고 넘어간다.
            log.warn("이력서 원본 파일 삭제 실패 (resumeId={}, keyId={}, errorType={})",
                    resumeId, Integer.toUnsignedString(s3Key.hashCode(), 16),
                    e.getClass().getSimpleName());
        }
    }
}
