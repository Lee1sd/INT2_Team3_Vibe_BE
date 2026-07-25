package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeSummaryResponse;
import com.careerdungeon.domain.resume.dto.ResumeUploadCompleteRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlResponse;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.exception.ResumeFileTypeNotAllowedException;
import com.careerdungeon.domain.resume.exception.ResumeNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeUploadNotFoundException;
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

@Service
public class ResumeService {
    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);
    private final ResumeRepository resumeRepository;
    private final ResumeCapacityPolicy capacityPolicy;
    private final ResumeFileValidator validator;
    private final ResumeFileStorage storage;
    private final ResumeUploadPersistenceService persistenceService;
    private final ResumeFileCleanupService cleanupService;

    public ResumeService(ResumeRepository resumeRepository, ResumeCapacityPolicy capacityPolicy,
                         ResumeFileValidator validator,
                         ResumeFileStorage storage, ResumeUploadPersistenceService persistenceService,
                         ResumeFileCleanupService cleanupService) {
        this.resumeRepository = resumeRepository;
        this.capacityPolicy = capacityPolicy;
        this.validator = validator;
        this.storage = storage;
        this.persistenceService = persistenceService;
        this.cleanupService = cleanupService;
    }

    public ResumeUploadUrlResponse issueUploadUrl(Long userId, ResumeUploadUrlRequest request) {
        capacityPolicy.ensureAvailable(userId, request.type());
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
        String requestedFileName;
        try {
            validator.validateSize(metadata.contentLength());
            requestedFileName = validateRequestedFileNameIfPresent(request.originalFileName(), extension);
        } catch (RuntimeException validationFailure) {
            cleanupInvalidUpload(key, metadata.eTag(), validationFailure);
            throw validationFailure;
        }
        byte[] bytes = storage.download(key, metadata.eTag());
        try {
            validator.validate(extension, bytes);
            String fallbackFileName = fallbackOriginalFileName(request.type(), extension);
            return persistenceService.persist(
                    userId, request.type(), key, calculateFileHash(bytes), metadata.eTag(),
                    requestedFileName, fallbackFileName, metadata.contentLength());
        } catch (RuntimeException original) {
            cleanupInvalidUpload(key, metadata.eTag(), original);
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
        String s3Etag = resume.getS3Etag();
        if (resumeRepository.softDeleteIfActive(resumeId, userId, Instant.now()) == 0) {
            throw new ResumeNotFoundException(resumeId);
        }
        cleanupService.enqueue(resumeId, s3Key, s3Etag);
    }

    private void validateOwnedPendingKey(Long userId, String key) {
        String prefix = "resumes/" + userId + "/pending/";
        if (!key.startsWith(prefix) || key.length() <= prefix.length()
                || key.substring(prefix.length()).contains("/")) {
            throw new ResumeUploadNotFoundException();
        }
    }

    /**
     * 요청에 파일명이 있으면 검증 후 그 값을 반환하고, 없으면(null/공백) {@code null}을 반환해
     * {@link ResumeUploadPersistenceService}가 재업로드 시 기존 값 유지 여부를 판단하게 한다.
     */
    private String validateRequestedFileNameIfPresent(String requestedName, String expectedExtension) {
        if (requestedName == null || requestedName.isBlank()) {
            return null;
        }
        return validateOriginalFileName(requestedName, expectedExtension);
    }

    private String fallbackOriginalFileName(ResumeType type, String extension) {
        String label = type == ResumeType.PORTFOLIO ? "포트폴리오" : "이력서";
        return label + "." + extension;
    }

    private String validateOriginalFileName(String requestedName, String expectedExtension) {
        String originalFileName = requestedName.trim();
        if (originalFileName.contains("/") || originalFileName.contains("\\")
                || !expectedExtension.equals(validator.validateExtension(originalFileName))) {
            throw new ResumeFileTypeNotAllowedException(originalFileName);
        }
        return originalFileName;
    }

    private String calculateFileHash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private void cleanupInvalidUpload(String key, String s3Etag, RuntimeException original) {
        try {
            storage.delete(key, s3Etag);
        } catch (RuntimeException cleanupFailure) {
            cleanupService.enqueue(null, key, s3Etag);
            original.addSuppressed(cleanupFailure);
            log.warn("Resume upload cleanup deferred (keyId={}, errorType={})",
                    Integer.toUnsignedString(key.hashCode(), 16), cleanupFailure.getClass().getSimpleName());
        }
    }
}
