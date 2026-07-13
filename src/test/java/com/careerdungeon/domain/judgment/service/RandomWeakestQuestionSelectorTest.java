package com.careerdungeon.domain.judgment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 운영 랜덤 최저점 선택기의 비확률적 계약을 검증한다. */
class RandomWeakestQuestionSelectorTest {

    private final RandomWeakestQuestionSelector sut = new RandomWeakestQuestionSelector();

    /** 후보가 하나면 난수와 무관하게 해당 문항을 반환한다. */
    @Test
    @DisplayName("최저점 후보가 하나면 해당 questionId를 반환한다")
    void returnsOnlyCandidate() {
        assertThat(sut.select(List.of(3))).isEqualTo(3);
    }

    /** 여러 번 선택해도 후보 밖의 문항이 반환되지 않는지 확인한다. */
    @Test
    @DisplayName("동점 선택 결과는 항상 전달한 후보 집합에 포함된다")
    void alwaysReturnsCandidate() {
        List<Integer> candidates = List.of(1, 2, 3);

        for (int attempt = 0; attempt < 100; attempt++) {
            assertThat(sut.select(candidates)).isIn(candidates);
        }
    }

    /** 선택할 후보가 없으면 명확하게 실패하는지 확인한다. */
    @Test
    @DisplayName("빈 최저점 후보 목록은 입력 오류로 거부한다")
    void rejectsEmptyCandidates() {
        assertThatThrownBy(() -> sut.select(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어");
        assertThatThrownBy(() -> sut.select(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어");
    }
}
