package com.careerdungeon.domain.resume.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    protected ResumeFileCleanupTask() {
    }

    public ResumeFileCleanupTask(Long resumeId, String s3Key) {
        this.resumeId = resumeId;
        this.s3Key = s3Key;
        this.createdAt = Instant.now();
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
}
