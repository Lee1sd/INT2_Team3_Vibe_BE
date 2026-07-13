package com.careerdungeon.domain.resume.repository;
import com.careerdungeon.domain.resume.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
}
