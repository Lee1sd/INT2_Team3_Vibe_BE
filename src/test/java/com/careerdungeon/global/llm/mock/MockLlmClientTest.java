package com.careerdungeon.global.llm.mock;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockLlmClientTest {

    private MockLlmClient sut;

    @BeforeEach
    void setUp() {
        sut = new MockLlmClient(18);
    }

    @Test
    @DisplayName("generateQuestions: 질문 4개 반환, turn 1~4, userName 포함")
    void generateQuestions_returnsFourQuestions() {
        QuestionGenerationRequest request = new QuestionGenerationRequest(
                "이력서 텍스트", "DB", "STRICT", "홍길동"
        );

        QuestionGenerationResponse response = sut.generateQuestions(request);

        assertThat(response.questions()).hasSize(4);
        assertThat(response.questions()).extracting("turn").containsExactly(1, 2, 3, 4);
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
                new QuestionAnswerPair(3, "질문3", "답변3", "모범3"),
                new QuestionAnswerPair(4, "질문4", "답변4", "모범4")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "LENIENT", "홍길동");

        InitialEvaluationResponse response = sut.evaluateInitialAnswers(request);

        assertThat(response.evaluations()).hasSize(4);
        assertThat(response.evaluations()).extracting("turn").containsExactly(1, 2, 3, 4);
        assertThat(response.evaluations()).allSatisfy(e -> {
            assertThat(e.score()).isBetween(0, 20);
            assertThat(e.feedback()).contains("홍길동님");
        });
        assertThat(response.totalScore())
                .isEqualTo(response.evaluations().stream().mapToInt(e -> e.score()).sum());
        assertThat(response.weakestQuestionId()).isBetween(1, 4);
        // 4문항 × 18점 = 72 < 80 → passed=false
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
            assertThat(e.technicalAccuracy()).isBetween(0, 8);
            assertThat(e.coreCoverage()).isBetween(0, 4);
            assertThat(e.reasoning()).isBetween(0, 3);
            assertThat(e.specificity()).isBetween(0, 3);
            assertThat(e.tradeOffsAndExceptions()).isBetween(0, 2);
        });
    }

    @Test
    @DisplayName("generateFollowUp: 꼬리질문과 모범답안을 반환한다")
    void generateFollowUp_returnsQuestionAndExpectedAnswer() {
        var response = sut.generateFollowUp(
                2,
                "캐시 정합성 문제를 어떻게 처리했나요?",
                "캐시는 DB 부하를 줄입니다.",
                "정합성 처리 전략이 빠져 있습니다.");

        assertThat(response.followUpQuestion()).contains("2번 질문");
        assertThat(response.followUpQuestion()).isNotBlank();
        assertThat(response.expectedAnswer()).contains("피드백");
        assertThat(response.expectedAnswer()).isNotBlank();
    }

    @Test
    @DisplayName("evaluateFinalAnswers IS-002b: turn 5 한 문항만 채점한다")
    void evaluateFinalAnswers_evaluatesOnlyFollowUpTurn() {
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(5, "꼬리질문", "꼬리답변", "꼬리모범답변"));
        var contexts = List.of(
                new PreviousEvaluationContext(1, "질문1", "답변1", 20, "피드백1"),
                new PreviousEvaluationContext(2, "질문2", "답변2", 10, "예외 상황 보완 필요"),
                new PreviousEvaluationContext(3, "질문3", "답변3", 19, "피드백3"),
                new PreviousEvaluationContext(4, "질문4", "답변4", 16, "피드백4"));
        var request = EvaluationRequest.finalEvaluation(pairs, contexts, "STRICT", "홍길동");

        FinalEvaluationResponse response = sut.evaluateFinalAnswers(request);

        assertThat(response.evaluations()).hasSize(1);
        assertThat(response.evaluations()).extracting("turn").containsExactly(5);
        assertThat(response.evaluations()).allSatisfy(e -> assertThat(e.feedback()).isNotBlank());
        assertThat(response.evaluations().stream()
                        .filter(e -> e.turn() == 5).findFirst().orElseThrow().feedback())
                .contains("홍길동님");
        assertThat(response.overallFeedback()).isNotBlank().contains("홍길동님");
        assertThat(response.overallFeedback())
                .contains("질문2", "예외 상황 보완 필요")
                .contains("🎯 총평")
                .contains("✨ 이런 점이 매우 훌륭했어요")
                .contains("🚀 합격을 확정 짓는 2%")
                .contains("💡 Next Step")
                .contains("❌ AS-IS (지원자의 기존 답변 방식)")
                .contains("⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)")
                .contains("※ 아래 수치는 답변 구조를 보여주기 위한 가상 예시이며, 실제 측정 결과가 아닙니다.")
                .contains("[예: p95 응답 시간 320ms → 140ms]")
                .doesNotContain("turn", "expectedAnswer", "모범답안", "confirmedScore", "루브릭");
        assertThat(response.overallFeedback().lines()
                .filter(line -> line.startsWith("- "))
                .toList()).hasSize(2);
        assertThat(response.totalScore()).isEqualTo(18);
        assertThat(response.passed()).isFalse();
    }

    @Test
    @DisplayName("evaluateFinalAnswers: 이전 평가 컨텍스트가 비어 있으면 명시적 입력 오류를 반환한다")
    void evaluateFinalAnswers_rejectsEmptyPreviousEvaluations() {
        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "꼬리답변", "꼬리모범답변")),
                List.of(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("turn 1~4 네 건");
    }

    @Test
    @DisplayName("evaluateInitialAnswers: 최초 4문항 합계와 무관하게 passed=false")
    void evaluateInitialAnswers_alwaysReturnsPassedFalse() {
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "q1", "a1", "e1"),
                new QuestionAnswerPair(2, "q2", "a2", "e2"),
                new QuestionAnswerPair(3, "q3", "a3", "e3"),
                new QuestionAnswerPair(4, "q4", "a4", "e4")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "STRICT", "김철수");

        InitialEvaluationResponse response = sut.evaluateInitialAnswers(request);

        assertThat(response.totalScore()).isEqualTo(72);
        assertThat(response.passed()).isFalse();
    }

    @Test
    @DisplayName("evaluateInitialAnswers: 최초 4문항이 만점이어도 꼬리질문 전에는 passed=false")
    void evaluateInitialAnswers_maximumInitialScoreRemainsFalse() {
        MockLlmClient passMock = new MockLlmClient(20);
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "q1", "a1", "e1"),
                new QuestionAnswerPair(2, "q2", "a2", "e2"),
                new QuestionAnswerPair(3, "q3", "a3", "e3"),
                new QuestionAnswerPair(4, "q4", "a4", "e4")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "STRICT", "김철수");

        InitialEvaluationResponse response = passMock.evaluateInitialAnswers(request);

        assertThat(response.totalScore()).isEqualTo(80);
        assertThat(response.passed()).isFalse();
    }

    @Test
    @DisplayName("Mock 점수 설정은 문항당 0~20 범위를 벗어날 수 없다")
    void constructor_rejectsOutOfRangeScore() {
        assertThatThrownBy(() -> new MockLlmClient(21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0~20");
    }
}
