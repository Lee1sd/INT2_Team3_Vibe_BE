package com.careerdungeon.domain.judgment.repository;

import com.careerdungeon.domain.judgment.entity.JudgmentResult;
import org.springframework.data.jpa.repository.JpaRepository;

/** 세션당 단일 최종 판정의 저장과 중복 여부 조회를 담당한다. */
public interface JudgmentResultRepository extends JpaRepository<JudgmentResult, Long> {

    /** 세션의 최종 판정이 이미 생성됐는지 확인한다. */
    boolean existsBySession_Id(Long sessionId);
}
