package com.careerdungeon.global.llm.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationRequestTest {

    @Test
    @DisplayName("questionAnswerPairs null → NullPointerException")
    void nullPairs_throws() {
        assertThatThrownBy(() -> new EvaluationRequest(null, "STRICT", "홍길동"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("questionAnswerPairs");
    }

    @Test
    @DisplayName("initial(): turn 1~3 3개 쌍 → 정상 생성")
    void initial_threePairs_ok() {
        var pairs = List.of(
                new QuestionAnswerPair(1, "q1", "a1", "e1"),
                new QuestionAnswerPair(2, "q2", "a2", "e2"),
                new QuestionAnswerPair(3, "q3", "a3", "e3")
        );
        assertThatCode(() -> EvaluationRequest.initial(pairs, "LENIENT", "홍길동"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("finalEvaluation(): turn 4 한 건으로 정상 생성")
    void finalEvaluation_followUpPair_ok() {
        var pairs = List.of(new QuestionAnswerPair(4, "꼬리질문", "답변", "모범답변"));
        var contexts = List.of(
                new PreviousEvaluationContext(1, "q1", "a1", 20, "f1"),
                new PreviousEvaluationContext(2, "q2", "a2", 15, "f2"),
                new PreviousEvaluationContext(3, "q3", "a3", 25, "f3"));
        assertThatCode(() -> EvaluationRequest.finalEvaluation(pairs, contexts, "STRICT", "홍길동"))
                .doesNotThrowAnyException();
    }
}
