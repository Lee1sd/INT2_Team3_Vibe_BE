package com.careerdungeon.domain.resume.repository;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    long countByUserIdAndType(Long userId, ResumeType type);
    Optional<Resume> findByIdAndUserId(Long id, Long userId);
}
