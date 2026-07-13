package com.careerdungeon.domain.judgment.service;

import java.util.List;

/** 최저점 동점 문항 중 하나를 선택하는 전략. */
@FunctionalInterface
public interface WeakestQuestionSelector {

    int select(List<Integer> candidateQuestionIds);
}
