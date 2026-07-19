package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeSummaryResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.event.ResumeUploadedEvent;
import com.careerdungeon.domain.resume.exception.ResumeFileTypeNotAllowedException;
import com.careerdungeon.domain.resume.exception.ResumeNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final int MAX_RESUME_PER_TYPE = 3;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "txt", "md");

    private final ResumeRepository resumeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ResumeService(ResumeRepository resumeRepository, ApplicationEventPublisher eventPublisher) {
        this.resumeRepository = resumeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ResumeResponse upload(Long userId, ResumeType type, MultipartFile file) {
        String extension = validateFileType(file);

        // FAILED는 실질적으로 점유한 슬롯이 아니므로(파싱 실패한 빈 자리) 개수 제한에서 제외한다.
        long validCount = resumeRepository.countByUserIdAndTypeAndParseStatusNot(userId, type, ParseStatus.FAILED);
        if (validCount >= MAX_RESUME_PER_TYPE) {
            throw new ResumeTypeLimitExceededException(type);
        }

        byte[] fileBytes = readBytes(file);
        String fileHash = calculateFileHash(fileBytes);

        // TODO(임시 구현): 실제 S3Client(PutObject)가 붙기 전까지, 로컬 임시 디렉터리에 파일을
        // 저장하고 그 경로를 s3Key로 대신 쓴다. PdfBoxResumeTextExtractor.fetchOriginalBytes()가
        // s3Key를 로컬 파일 경로로 취급하는 임시 구현과 짝을 이루는 TODO — S3Client 연동 시 이
        // 메서드와 fetchOriginalBytes를 함께 실제 S3 호출로 교체해야 한다.
        String s3Key = saveToLocalTempFile(fileBytes, extension, file.getContentType());
        registerRollbackCleanup(s3Key);

        try {
            Resume resume = persistUpload(userId, type, s3Key, fileHash);
            eventPublisher.publishEvent(new ResumeUploadedEvent(resume.getId()));
            return ResumeResponse.uploaded(resume.getId(), resume.getType(), resume.getParseStatus());
        } catch (RuntimeException e) {
            deleteStoredFileOnFailure(s3Key, e);
            throw e;
        }
    }

    public ResumeResponse getStatus(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        return ResumeResponse.of(resume);
    }

    public List<ResumeSummaryResponse> getResumes(Long userId) {
        return resumeRepository.findByUserIdOrderByLastUploadedAtDesc(userId).stream()
                .map(ResumeSummaryResponse::from)
                .toList();
    }

    private String validateFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String extension = extractExtension(filename);
        if (extension == null || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResumeFileTypeNotAllowedException(filename);
        }
        return extension;
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return null;
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 읽을 수 없습니다.", e);
        }
    }

    // 재업로드 시 동일 파일 여부 판단(UPSERT 등)에 쓰이는 원본 파일 해시 (SHA-256)
    private String calculateFileHash(byte[] fileBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(fileBytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private Resume persistUpload(Long userId, ResumeType type, String s3Key, String fileHash) {
        // 이미 FAILED로 남은 슬롯이 있으면 새로 insert하지 않고 그 슬롯을 재사용(UPSERT)한다 —
        // 사용자가 실패한 파일을 스스로 재시도할 수 있는 유일한 경로.
        return resumeRepository.findFirstByUserIdAndTypeAndParseStatus(userId, type, ParseStatus.FAILED)
                .map(failed -> {
                    failed.replaceUpload(s3Key, fileHash);
                    return failed;
                })
                .orElseGet(() -> resumeRepository.save(new Resume(userId, type, s3Key, fileHash)));
    }

    private void registerRollbackCleanup(String s3Key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteStoredFileAfterRollback(s3Key);
                }
            }
        });
    }

    // TODO(임시 구현): S3Client 연동 시 이 메서드를 실제 PutObject 호출로 교체한다.
    private String saveToLocalTempFile(byte[] fileBytes, String extension, String declaredContentType) {
        try {
            Path tempFile = Files.createTempFile("resume-", "." + extension);
            Files.write(tempFile, fileBytes);
            logContentTypeHints(tempFile, declaredContentType);
            return tempFile.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 임시 저장할 수 없습니다.", e);
        }
    }

    // 클라이언트 Content-Type과 OS별 probe 결과는 위조되거나 환경마다 달라질 수 있으므로
    // 관찰용으로만 기록한다. 허용 여부는 정규화한 확장자와 추출기 내부 내용 검증이 결정한다.
    private void logContentTypeHints(Path tempFile, String declaredContentType) {
        try {
            log.debug("이력서 파일 타입 참고 정보 (declared={}, probed={})",
                    declaredContentType, Files.probeContentType(tempFile));
        } catch (IOException e) {
            log.debug("이력서 파일 타입 probe 실패 (declared={})", declaredContentType, e);
        }
    }

    private void deleteStoredFileOnFailure(String s3Key, RuntimeException originalException) {
        try {
            deleteStoredFile(s3Key);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    private void deleteStoredFileAfterRollback(String s3Key) {
        try {
            deleteStoredFile(s3Key);
        } catch (IOException cleanupException) {
            log.warn("롤백된 이력서 업로드 파일 삭제 실패 (path={})", s3Key, cleanupException);
        }
    }

    // TODO(임시 구현): S3Client 연동 시 이 메서드를 실제 DeleteObject 호출로 교체한다.
    private void deleteStoredFile(String s3Key) throws IOException {
        Files.deleteIfExists(Path.of(s3Key));
    }
}
