package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ResumeFileCleanupTask;
import com.careerdungeon.domain.resume.repository.ResumeFileCleanupTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ResumeFileCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ResumeFileCleanupService.class);

    private final ResumeFileCleanupTaskRepository cleanupTaskRepository;

    public ResumeFileCleanupService(ResumeFileCleanupTaskRepository cleanupTaskRepository) {
        this.cleanupTaskRepository = cleanupTaskRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanup(Long resumeId, String s3Key) {
        if (s3Key == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(s3Key));
        } catch (IOException | RuntimeException cleanupException) {
            cleanupTaskRepository.save(new ResumeFileCleanupTask(resumeId, s3Key));
            log.warn("이력서 원본 파일 정리 실패: 수동 재시도 필요 (resumeId={}, keyId={}, errorType={})",
                    resumeId, maskedKeyId(s3Key), cleanupException.getClass().getSimpleName());
        }
    }

    private String maskedKeyId(String s3Key) {
        return Integer.toUnsignedString(s3Key.hashCode(), 16);
    }
}
