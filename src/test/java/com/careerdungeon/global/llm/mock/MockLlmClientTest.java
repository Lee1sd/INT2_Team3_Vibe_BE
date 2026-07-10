package com.careerdungeon.global.llm.mock;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.EvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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
    @DisplayName("evaluateAnswers IS-002: evaluations·totalScore·weakestQuestionId·passed 스키마 충족")
    void evaluateAnswers_returnsValidSchema() {
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "질문1", "답변1", "모범1"),
                new QuestionAnswerPair(2, "질문2", "답변2", "모범2"),
                new QuestionAnswerPair(3, "질문3", "답변3", "모범3")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "LENIENT", "홍길동");

        EvaluationResponse response = sut.evaluateAnswers(request);

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
    @DisplayName("evaluateAnswers IS-002b: retainedTurns={1,2} + followUpTurn=4 → turns {1,2,4}, turn 4 feedback 포함")
    void evaluateAnswers_followUpRequest_returnsThreeEvaluationsWithFollowUpFeedback() {
        var request = EvaluationRequest.followUp(
                new QuestionAnswerPair(4, "꼬리질문", "답변", "모범답변"),
                "STRICT", "홍길동", Set.of(1, 2));

        EvaluationResponse response = sut.evaluateAnswers(request);

        assertThat(response.evaluations()).hasSize(3);
        assertThat(response.evaluations()).extracting("turn").containsExactlyInAnyOrder(1, 2, 4);
        assertThat(response.evaluations().stream()
                .filter(e -> e.turn() == 4).findFirst().orElseThrow().feedback())
                .isNotBlank().contains("홍길동님");
        assertThat(response.evaluations().stream()
                .filter(e -> e.turn() != 4).toList())
                .allSatisfy(e -> assertThat(e.feedback()).isEmpty());
        assertThat(response.weakestQuestionId()).isEqualTo(0);
    }

    @Test
    @DisplayName("evaluateAnswers IS-002b: retainedTurns={1,2} → turn 3 미포함, turns {1,2,4} 확인")
    void evaluateAnswers_followUpRequest_retainedTurns12_turn3Excluded() {
        var request = EvaluationRequest.followUp(
                new QuestionAnswerPair(4, "꼬리질문", "답변", "모범답변"),
                "STRICT", "김철수", Set.of(1, 2));

        EvaluationResponse response = sut.evaluateAnswers(request);

        assertThat(response.evaluations()).extracting("turn")
                .doesNotContain(3)
                .containsExactlyInAnyOrder(1, 2, 4);
    }

    @Test
    @DisplayName("evaluateAnswers IS-002b: retainedTurns={2,3} → turn 1 미포함, turns {2,3,4} 확인 (동적 검증)")
    void evaluateAnswers_followUpRequest_retainedTurns23_turn1Excluded() {
        // retainedTurns를 caller가 전달 — turn 1이 weakest였던 경우
        var request = EvaluationRequest.followUp(
                new QuestionAnswerPair(4, "꼬리질문", "답변", "모범답변"),
                "STRICT", "김철수", Set.of(2, 3));

        EvaluationResponse response = sut.evaluateAnswers(request);

        assertThat(response.evaluations()).extracting("turn")
                .doesNotContain(1)
                .containsExactlyInAnyOrder(2, 3, 4);
    }

    @Test
    @DisplayName("evaluateAnswers: totalScore < 80 이면 passed=false (합격 기준 80점, FR-05)")
    void evaluateAnswers_passedFalseWhenScoreBelow80() {
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "q1", "a1", "e1"),
                new QuestionAnswerPair(2, "q2", "a2", "e2"),
                new QuestionAnswerPair(3, "q3", "a3", "e3"),
                new QuestionAnswerPair(4, "q4", "a4", "e4")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "STRICT", "김철수");

        EvaluationResponse response = sut.evaluateAnswers(request);

        // 문항당 18점 → 4문항 합계 72 < 80 → passed=false
        assertThat(response.totalScore()).isEqualTo(72);
        assertThat(response.passed()).isFalse();
    }

    @Test
    @DisplayName("evaluateAnswers: totalScore >= 80 이면 passed=true — score-per-question=20 주입")
    void evaluateAnswers_passedTrueWhenScoreReaches80() {
        MockLlmClient passMock = new MockLlmClient(20);
        List<QuestionAnswerPair> pairs = List.of(
                new QuestionAnswerPair(1, "q1", "a1", "e1"),
                new QuestionAnswerPair(2, "q2", "a2", "e2"),
                new QuestionAnswerPair(3, "q3", "a3", "e3"),
                new QuestionAnswerPair(4, "q4", "a4", "e4")
        );
        EvaluationRequest request = EvaluationRequest.initial(pairs, "STRICT", "김철수");

        EvaluationResponse response = passMock.evaluateAnswers(request);

        // 문항당 20점 → 4문항 합계 80 >= 80 → passed=true
        assertThat(response.totalScore()).isEqualTo(80);
        assertThat(response.passed()).isTrue();
    }
}
