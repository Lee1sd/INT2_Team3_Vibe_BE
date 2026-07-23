package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeSummaryResponse;
import com.careerdungeon.domain.resume.dto.ResumeUploadCompleteRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.exception.ResumeNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeUploadNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class ResumeService {
    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private static final int MAX_RESUME_PER_TYPE = 3;

    private final ResumeRepository resumeRepository;
    private final ResumeFileValidator validator;
    private final ResumeFileStorage storage;
    private final ResumeUploadPersistenceService persistenceService;
    private final ResumeFileCleanupService cleanupService;

    public ResumeService(ResumeRepository resumeRepository, ResumeFileValidator validator,
                         ResumeFileStorage storage, ResumeUploadPersistenceService persistenceService,
                         ResumeFileCleanupService cleanupService) {
        this.resumeRepository = resumeRepository;
        this.validator = validator;
        this.storage = storage;
        this.persistenceService = persistenceService;
        this.cleanupService = cleanupService;
    }

    public ResumeUploadUrlResponse issueUploadUrl(Long userId, ResumeUploadUrlRequest request) {
        ensureCapacity(userId, request.type());
        String extension = validator.validateExtension(request.fileName());
        validator.validateSize(request.fileSize());
        PresignedResumeUpload upload = storage.createPresignedUpload(
                userId, extension, request.fileSize(), request.contentType());
        return new ResumeUploadUrlResponse(upload.uploadUrl(), upload.s3Key(), upload.expiresInSeconds());
    }

    public ResumeResponse completeUpload(Long userId, ResumeUploadCompleteRequest request) {
        String key = request.s3Key();
        validateOwnedPendingKey(userId, key);
        String extension = validator.validateExtension(key);
        StoredResumeFileMetadata metadata = storage.metadata(key);
        try {
            validator.validateSize(metadata.contentLength());
        } catch (RuntimeException validationFailure) {
            cleanupInvalidUpload(key, validationFailure);
            throw validationFailure;
        }
        byte[] bytes = storage.download(key, metadata.eTag());
        try {
            validator.validate(extension, bytes);
            return persistenceService.persist(
                    userId, request.type(), key, calculateFileHash(bytes), metadata.eTag());
        } catch (RuntimeException original) {
            cleanupInvalidUpload(key, original);
            throw original;
        }
    }

    public ResumeResponse getStatus(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findActiveByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        return ResumeResponse.of(resume);
    }

    public List<ResumeSummaryResponse> getResumes(Long userId) {
        return resumeRepository.findByUserIdOrderByLastUploadedAtDesc(userId).stream()
                .map(ResumeSummaryResponse::from).toList();
    }

    @Transactional
    public void delete(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findActiveByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResumeNotFoundException(resumeId));
        String s3Key = resume.getS3Key();
        if (resumeRepository.softDeleteIfActive(resumeId, userId, Instant.now()) == 0) {
            throw new ResumeNotFoundException(resumeId);
        }
        cleanupService.enqueue(resumeId, s3Key);
    }

    private void ensureCapacity(Long userId, com.careerdungeon.domain.resume.entity.ResumeType type) {
        long count = resumeRepository.countByUserIdAndTypeAndParseStatusNotInAndDeletedAtIsNull(
                userId, type, Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED));
        if (count >= MAX_RESUME_PER_TYPE) throw new ResumeTypeLimitExceededException(type);
    }

    private void validateOwnedPendingKey(Long userId, String key) {
        String prefix = "resumes/" + userId + "/pending/";
        if (!key.startsWith(prefix) || key.length() <= prefix.length()
                || key.substring(prefix.length()).contains("/")) {
            throw new ResumeUploadNotFoundException();
        }
    }

    private String calculateFileHash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private void cleanupInvalidUpload(String key, RuntimeException original) {
        try {
            storage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            cleanupService.enqueue(null, key);
            original.addSuppressed(cleanupFailure);
            log.warn("Resume upload cleanup deferred (keyId={}, errorType={})",
                    Integer.toUnsignedString(key.hashCode(), 16), cleanupFailure.getClass().getSimpleName());
        }
    }
}
