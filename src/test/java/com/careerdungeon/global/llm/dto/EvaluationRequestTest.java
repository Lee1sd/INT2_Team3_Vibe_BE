package com.careerdungeon.global.llm.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationRequestTest {

    @Test
    @DisplayName("followUp(): retainedTurns null → NullPointerException")
    void followUp_nullRetainedTurns_throws() {
        var pair = new QuestionAnswerPair(4, "꼬리질문", "답변", "모범답변");
        assertThatThrownBy(() -> EvaluationRequest.followUp(pair, "STRICT", "홍길동", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("retainedTurns");
    }

    @Test
    @DisplayName("followUp(): retainedTurns non-null → 정상 생성")
    void followUp_validRetainedTurns_ok() {
        var pair = new QuestionAnswerPair(4, "꼬리질문", "답변", "모범답변");
        assertThatCode(() -> EvaluationRequest.followUp(pair, "STRICT", "홍길동", Set.of(1, 2)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("initial(): retainedTurns 항상 null — 예외 없음")
    void initial_retainedTurnsAlwaysNull_ok() {
        var pairs = List.of(new QuestionAnswerPair(1, "q", "a", "e"));
        var request = EvaluationRequest.initial(pairs, "LENIENT", "홍길동");
        assertThatCode(() -> {
            assert request.retainedTurns() == null;
        }).doesNotThrowAnyException();
    }
}
