package com.careerdungeon.domain.judgment.repository;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 답변 중복 제출과 최종 판정 경합을 직렬화하기 위한 세션 잠금 조회 포트다. */
public interface JudgmentSessionRepository extends Repository<InterviewSession, Long> {

    /** 동일 세션의 동시 답변 제출을 직렬화하도록 쓰기 잠금으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from InterviewSession session where session.id = :sessionId")
    Optional<InterviewSession> findByIdForUpdate(@Param("sessionId") Long sessionId);
}
