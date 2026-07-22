package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ResumeFileCleanupTask;
import com.careerdungeon.domain.resume.repository.ResumeFileCleanupTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeFileCleanupService {

    private static final Logger log = LoggerFactory.getLogger(ResumeFileCleanupService.class);

    private final ResumeFileCleanupTaskRepository cleanupTaskRepository;
    private final ResumeFileStorage resumeFileStorage;

    public ResumeFileCleanupService(ResumeFileCleanupTaskRepository cleanupTaskRepository,
                                    ResumeFileStorage resumeFileStorage) {
        this.cleanupTaskRepository = cleanupTaskRepository;
        this.resumeFileStorage = resumeFileStorage;
    }

    @Transactional
    public void enqueue(Long resumeId, String s3Key) {
        if (s3Key != null) {
            cleanupTaskRepository.save(new ResumeFileCleanupTask(resumeId, s3Key));
        }
    }

    @Scheduled(fixedDelayString = "${resume.cleanup.fixed-delay-ms:600000}")
    @Transactional
    public void retryPendingTasks() {
        for (ResumeFileCleanupTask task : cleanupTaskRepository.findTop100ByOrderByCreatedAtAsc()) {
            try {
                resumeFileStorage.delete(task.getS3Key());
                cleanupTaskRepository.delete(task);
            } catch (Exception cleanupException) {
                task.markFailed();
                log.warn("이력서 원본 파일 정리 실패: 다음 주기에 재시도 (resumeId={}, keyId={}, errorType={})",
                        task.getResumeId(), maskedKeyId(task.getS3Key()),
                        cleanupException.getClass().getSimpleName());
            }
        }
    }

    private String maskedKeyId(String s3Key) {
        return Integer.toUnsignedString(s3Key.hashCode(), 16);
    }
}
