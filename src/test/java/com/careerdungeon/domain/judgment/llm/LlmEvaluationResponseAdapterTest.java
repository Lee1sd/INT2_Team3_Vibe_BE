package com.careerdungeon.domain.judgment.llm;

import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawQuestionEvaluation;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmEvaluationResponseAdapterTest {

    private final LlmEvaluationResponseAdapter sut = new LlmEvaluationResponseAdapter();

    @Test
    void toRawInitial_mapsAllFields() {
        InitialEvaluationResponse response = new InitialEvaluationResponse(List.of(
                evaluation(1, 12, "feedback 1"),
                evaluation(2, 20, "feedback 2")
        ), 32, 1, false);

        RawInitialEvaluationResponse raw = sut.toRawInitial(response);

        assertThat(raw.totalScore()).isEqualTo(32);
        assertThat(raw.weakestQuestionId()).isEqualTo(1);
        assertThat(raw.passed()).isFalse();
        assertThat(raw.evaluations()).hasSize(2);
        RawQuestionEvaluation first = raw.evaluations().get(0);
        assertThat(first.questionId()).isEqualTo(1);
        assertThat(first.score()).isEqualTo(12);
        assertThat(first.feedback()).isEqualTo("feedback 1");
        assertThat(first.rubricScores().technicalAccuracy()).isEqualTo(10);
        assertThat(first.rubricScores().coreCoverage()).isEqualTo(5);
        assertThat(first.rubricScores().reasoning()).isEqualTo(4);
        assertThat(first.rubricScores().specificity()).isEqualTo(3);
        assertThat(first.rubricScores().tradeOffsAndExceptions()).isEqualTo(3);
    }

    @Test
    void toRawFinal_mapsAllFields() {
        FinalEvaluationResponse response = new FinalEvaluationResponse(List.of(
                evaluation(4, 18, "follow-up feedback")
        ), 18, true, "overall feedback");

        RawFinalEvaluationResponse raw = sut.toRawFinal(response);

        assertThat(raw.totalScore()).isEqualTo(18);
        assertThat(raw.passed()).isTrue();
        assertThat(raw.overallFeedback()).isEqualTo("overall feedback");
        assertThat(raw.evaluations()).singleElement().satisfies(evaluation -> {
            assertThat(evaluation.questionId()).isEqualTo(4);
            assertThat(evaluation.score()).isEqualTo(18);
            assertThat(evaluation.feedback()).isEqualTo("follow-up feedback");
            assertThat(evaluation.rubricScores().technicalAccuracy()).isEqualTo(10);
            assertThat(evaluation.rubricScores().coreCoverage()).isEqualTo(5);
            assertThat(evaluation.rubricScores().reasoning()).isEqualTo(4);
            assertThat(evaluation.rubricScores().specificity()).isEqualTo(3);
            assertThat(evaluation.rubricScores().tradeOffsAndExceptions()).isEqualTo(3);
        });
    }

    @Test
    void toRawInitial_whenResponseNull_throwsSchemaValidationException() {
        assertThatThrownBy(() -> sut.toRawInitial(null))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("InitialEvaluationResponse");
    }

    @Test
    void toRawFinal_whenEvaluationListNull_throwsSchemaValidationException() {
        FinalEvaluationResponse response = new FinalEvaluationResponse(null, 0, false, "feedback");

        assertThatThrownBy(() -> sut.toRawFinal(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("evaluations");
    }

    @Test
    void toRawInitial_whenEvaluationElementNull_throwsSchemaValidationException() {
        InitialEvaluationResponse response = new InitialEvaluationResponse(
                java.util.Arrays.asList(evaluation(1, 10, "feedback"), null),
                10,
                1,
                false);

        assertThatThrownBy(() -> sut.toRawInitial(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("null");
    }

    private QuestionEvaluation evaluation(int turn, int score, String feedback) {
        return new QuestionEvaluation(turn, score, 10, 5, 4, 3, 3, feedback);
    }
}
