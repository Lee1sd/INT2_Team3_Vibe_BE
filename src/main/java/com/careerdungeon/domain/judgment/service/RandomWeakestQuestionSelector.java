package com.careerdungeon.domain.judgment.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** FR-04의 최저점 동점 랜덤 선택 정책. */
@Component
public class RandomWeakestQuestionSelector implements WeakestQuestionSelector {

    /**
     * FR-04 요구사항에 따라 동점 후보를 균등한 확률로 무작위 선택한다.
     *
     * @param candidateQuestionIds 최저점 동점 문항 식별자 목록
     * @return 무작위로 선택된 문항 식별자
     */
    @Override
    public int select(List<Integer> candidateQuestionIds) {
        if (candidateQuestionIds == null || candidateQuestionIds.isEmpty()) {
            throw new IllegalArgumentException("최저점 후보가 비어 있습니다.");
        }
        // 공유 상태가 없는 ThreadLocalRandom을 사용해 동시 요청에서도 별도 동기화가 필요 없다.
        return candidateQuestionIds.get(ThreadLocalRandom.current().nextInt(candidateQuestionIds.size()));
    }
}
