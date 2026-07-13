package com.careerdungeon.domain.judgment.service;

import java.util.List;

/** 최저점 동점 문항 중 하나를 선택하는 전략. */
@FunctionalInterface
public interface WeakestQuestionSelector {

    /**
     * 동일한 최저 점수를 받은 문항 후보 중 하나를 선택한다.
     *
     * @param candidateQuestionIds 최저점 동점 문항 식별자 목록
     * @return 선택된 문항 식별자
     */
    int select(List<Integer> candidateQuestionIds);
}
