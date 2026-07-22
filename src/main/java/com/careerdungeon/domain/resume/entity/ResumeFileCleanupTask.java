package com.careerdungeon.domain.resume.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "resume_file_cleanup_tasks")
public class ResumeFileCleanupTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Column(name = "s3_key", nullable = false, length = 255)
    private String s3Key;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileCleanupStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    protected ResumeFileCleanupTask() {
    }

    public ResumeFileCleanupTask(Long resumeId, String s3Key) {
        this.resumeId = resumeId;
        this.s3Key = s3Key;
        this.createdAt = Instant.now();
        this.status = FileCleanupStatus.PENDING;
    }

    public Long getResumeId() {
        return resumeId;
    }

    public String getS3Key() {
        return s3Key;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public FileCleanupStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void markFailed() {
        this.status = FileCleanupStatus.FAILED;
        this.attemptCount++;
        this.lastAttemptAt = Instant.now();
    }
}
