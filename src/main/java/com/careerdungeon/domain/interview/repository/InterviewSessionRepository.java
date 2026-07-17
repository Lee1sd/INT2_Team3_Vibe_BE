package com.careerdungeon.domain.interview.repository;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
}
