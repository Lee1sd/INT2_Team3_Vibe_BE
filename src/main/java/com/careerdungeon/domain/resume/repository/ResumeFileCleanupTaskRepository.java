package com.careerdungeon.domain.resume.repository;

import com.careerdungeon.domain.resume.entity.ResumeFileCleanupTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeFileCleanupTaskRepository extends JpaRepository<ResumeFileCleanupTask, Long> {
}
