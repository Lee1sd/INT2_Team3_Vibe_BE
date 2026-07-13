package com.careerdungeon.domain.judgment.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** FR-04의 최저점 동점 랜덤 선택 정책. */
@Component
public class RandomWeakestQuestionSelector implements WeakestQuestionSelector {

    @Override
    public int select(List<Integer> candidateQuestionIds) {
        if (candidateQuestionIds == null || candidateQuestionIds.isEmpty()) {
            throw new IllegalArgumentException("최저점 후보가 비어 있습니다.");
        }
        return candidateQuestionIds.get(ThreadLocalRandom.current().nextInt(candidateQuestionIds.size()));
    }
}
