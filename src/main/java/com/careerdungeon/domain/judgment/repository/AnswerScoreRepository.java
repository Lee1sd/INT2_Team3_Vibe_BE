package com.careerdungeon.domain.judgment.repository;

import com.careerdungeon.domain.judgment.entity.AnswerScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 세션별 서버 확정 문항 점수를 저장하고 turn 순서로 조회한다. */
public interface AnswerScoreRepository extends JpaRepository<AnswerScore, Long> {

    /** 세션의 확정 점수를 turn 오름차순으로 조회한다. */
    List<AnswerScore> findAllBySession_IdOrderByTurnAsc(Long sessionId);

    /** 세션에 하나 이상의 확정 점수가 이미 있는지 확인한다. */
    boolean existsBySession_Id(Long sessionId);
}
