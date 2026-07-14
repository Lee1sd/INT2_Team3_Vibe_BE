package com.careerdungeon.global.llm.mock;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmClientTest {

    private MockLlmClient sut;

    @BeforeEach
    void setUp() {
        sut = new MockLlmClient(18);
    }

    @Test
    @DisplayName("generateQuestions: 질문 3개 반환, turn 1~3, userName 포함")
    void generateQuestions_returnsThreeQuestions() {
        QuestionGenerationRequest request = new QuestionGenerationRequest(
                "이력서 텍스트", "DB", "STRICT", "홍길동"
        );

        QuestionGenerationResponse response = sut.generateQuestions(request);

        assertThat(response.questions()).hasSize(3);
        assertThat(response.questions()).extracting("turn").containsExactly(1, 2, 3);
        assertThat(response.questions().get(0).questionText()).contains("홍길동님");
        assertThat(response.questions()).allSatisfy(q -> {
            assertThat(q.questionText()).isNotBlank();
            assertThat(q.expectedAnswer()).isNotBlank();
        });
    }

    @Test
    @DisplayName("evaluateInitialAnswers IS-002: evaluations·totalScore·weakestQuestionId·passed 스키마 충족")
    void evaluateAnswers_returnsValidSchema() {
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "질문1", "답변1", "모범1"),
                new QuestionAnswerPair(2, "질문2", "답변2", "모범2"),
                new QuestionAnswerPair(3, "질문3", "답변3", "모범3")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "LENIENT", "홍길동");

        InitialEvaluationResponse response = sut.evaluateInitialAnswers(request);

        assertThat(response.evaluations()).hasSize(3);
        assertThat(response.evaluations()).extracting("turn").containsExactly(1, 2, 3);
        assertThat(response.evaluations()).allSatisfy(e -> {
            assertThat(e.score()).isBetween(0, 25);
            assertThat(e.feedback()).contains("홍길동님");
        });
        assertThat(response.totalScore())
                .isEqualTo(response.evaluations().stream().mapToInt(e -> e.score()).sum());
        assertThat(response.weakestQuestionId()).isBetween(1, 3);
        // 3문항 × 18점 = 54 < 80 → passed=false
        assertThat(response.passed()).isFalse();
    }

    @Test
    @DisplayName("evaluations의 5개 루브릭 합계가 score와 정확히 일치한다 (ADR-010)")
    void evaluateInitialAnswers_rubricScoresSumToScore() {
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "질문1", "답변1", "모범1")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "LENIENT", "홍길동");

        InitialEvaluationResponse response = sut.evaluateInitialAnswers(request);

        assertThat(response.evaluations()).allSatisfy(e -> {
            int rubricSum = e.technicalAccuracy() + e.coreCoverage() + e.reasoning()
                    + e.specificity() + e.tradeOffsAndExceptions();
            assertThat(rubricSum).isEqualTo(e.score());
            assertThat(e.technicalAccuracy()).isBetween(0, 10);
            assertThat(e.coreCoverage()).isBetween(0, 5);
            assertThat(e.reasoning()).isBetween(0, 4);
            assertThat(e.specificity()).isBetween(0, 3);
            assertThat(e.tradeOffsAndExceptions()).isBetween(0, 3);
        });
    }

    @Test
    @DisplayName("evaluateFinalAnswers IS-002b: turn {1,2,3,4} 전체를 채점, 각 문항 실제 평가(빈 feedback 없음)")
    void evaluateFinalAnswers_evaluatesAllFourTurns() {
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "질문1", "답변1", "모범1"),
                new QuestionAnswerPair(2, "질문2", "답변2", "모범2"),
                new QuestionAnswerPair(3, "질문3", "답변3", "모범3"),
                new QuestionAnswerPair(4, "꼬리질문", "꼬리답변", "꼬리모범답변")
        );
        var request = EvaluationRequest.finalEvaluation(pairs, "STRICT", "홍길동");

        FinalEvaluationResponse response = sut.evaluateFinalAnswers(request);

        assertThat(response.evaluations()).hasSize(4);
        assertThat(response.evaluations()).extracting("turn").containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(response.evaluations()).allSatisfy(e -> assertThat(e.feedback()).isNotBlank());
        assertThat(response.evaluations().stream()
                        .filter(e -> e.turn() == 4).findFirst().orElseThrow().feedback())
                .contains("홍길동님");
        assertThat(response.overallFeedback()).isNotBlank().contains("홍길동님");
        // 4문항 × 18점 = 72
        assertThat(response.totalScore()).isEqualTo(72);
    }

    @Test
    @DisplayName("evaluateInitialAnswers: totalScore < 80 이면 passed=false (합격 기준 80점, FR-05)")
    void evaluateInitialAnswers_passedFalseWhenScoreBelow80() {
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "q1", "a1", "e1"),
                new QuestionAnswerPair(2, "q2", "a2", "e2"),
                new QuestionAnswerPair(3, "q3", "a3", "e3"),
                new QuestionAnswerPair(4, "q4", "a4", "e4")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "STRICT", "김철수");

        InitialEvaluationResponse response = sut.evaluateInitialAnswers(request);

        // 문항당 18점 → 4문항 합계 72 < 80 → passed=false
        assertThat(response.totalScore()).isEqualTo(72);
        assertThat(response.passed()).isFalse();
    }

    @Test
    @DisplayName("evaluateInitialAnswers: totalScore >= 80 이면 passed=true — score-per-question=20 주입")
    void evaluateInitialAnswers_passedTrueWhenScoreReaches80() {
        MockLlmClient passMock = new MockLlmClient(20);
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "q1", "a1", "e1"),
                new QuestionAnswerPair(2, "q2", "a2", "e2"),
                new QuestionAnswerPair(3, "q3", "a3", "e3"),
                new QuestionAnswerPair(4, "q4", "a4", "e4")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "STRICT", "김철수");

        InitialEvaluationResponse response = passMock.evaluateInitialAnswers(request);

        // 문항당 20점 → 4문항 합계 80 >= 80 → passed=true
        assertThat(response.totalScore()).isEqualTo(80);
        assertThat(response.passed()).isTrue();
    }
}
