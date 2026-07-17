package com.careerdungeon.domain.interview.repository;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 면접 세션의 기본 조회와 답변 제출용 잠금 조회를 제공한다. */
public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    /** 답변 제출 준비와 결과 반영 시 동일 세션의 상태 전이를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from InterviewSession session where session.id = :sessionId")
    Optional<InterviewSession> findByIdForUpdate(@Param("sessionId") Long sessionId);
}
