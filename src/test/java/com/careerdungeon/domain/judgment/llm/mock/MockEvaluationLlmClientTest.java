package com.careerdungeon.domain.judgment.llm.mock;

import com.careerdungeon.domain.judgment.llm.dto.EvaluationRequest;
import com.careerdungeon.domain.judgment.llm.dto.QuestionAnswerPair;
import com.careerdungeon.domain.judgment.llm.dto.RawEvaluationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockEvaluationLlmClientTest {

    private final MockEvaluationLlmClient sut = new MockEvaluationLlmClient();

    @Test
    @DisplayName("동일한 사용자 답변도 모범답변의 핵심어 충족도에 따라 점수가 달라진다")
    void expectedAnswerChangesScore() {
        String answer = "인덱스는 카디널리티가 높은 컬럼의 where와 join 조회 성능을 개선합니다.";

        int matchingScore = evaluateOne(answer,
                "카디널리티가 높은 컬럼, where join 조회 성능 개선").evaluations().get(0).score();
        int unrelatedScore = evaluateOne(answer,
                "가비지 컬렉션 stop the world young old generation").evaluations().get(0).score();

        assertThat(matchingScore).isGreaterThan(unrelatedScore);
    }

    @Test
    @DisplayName("무응답은 5개 루브릭과 문항 점수가 모두 0점이다")
    void blankAnswerScoresZero() {
        var evaluation = evaluateOne("  ", "인덱스 카디널리티 조회 성능").evaluations().get(0);

        assertThat(evaluation.score()).isZero();
        assertThat(evaluation.rubricScores().technicalAccuracy()).isZero();
        assertThat(evaluation.rubricScores().coreCoverage()).isZero();
        assertThat(evaluation.rubricScores().reasoning()).isZero();
        assertThat(evaluation.rubricScores().specificity()).isZero();
        assertThat(evaluation.rubricScores().tradeOffsAndExceptions()).isZero();
    }

    @Test
    @DisplayName("4문항 채점은 누락 없는 20개 루브릭 숫자와 기존 상위 필드를 반환한다")
    void fourQuestionsReturnCompleteRubricSchema() {
        List<QuestionAnswerPair> pairs = List.of(
                pair(1, "답변 1"),
                pair(2, "답변 2"),
                pair(3, "답변 3"),
                pair(4, "답변 4"));

        RawEvaluationResponse response = sut.evaluate(new EvaluationRequest(pairs, "strict", "최용성"));

        assertThat(response.evaluations()).hasSize(4).allSatisfy(evaluation -> {
            assertThat(evaluation.score()).isBetween(0, 25);
            assertThat(evaluation.rubricScores().technicalAccuracy()).isNotNull();
            assertThat(evaluation.rubricScores().coreCoverage()).isNotNull();
            assertThat(evaluation.rubricScores().reasoning()).isNotNull();
            assertThat(evaluation.rubricScores().specificity()).isNotNull();
            assertThat(evaluation.rubricScores().tradeOffsAndExceptions()).isNotNull();
        });
        assertThat(response.totalScore()).isNotNull();
        assertThat(response.weakestQuestionId()).isNotNull();
        assertThat(response.passed()).isNotNull();
        assertThat(response.overallFeedback()).contains("최용성");
    }

    @Test
    @DisplayName("같은 요청은 항상 같은 원시 점수를 반환해 테스트가 결정적이다")
    void evaluationIsDeterministic() {
        EvaluationRequest request = new EvaluationRequest(
                List.of(pair(1, "인덱스는 조회 성능 때문에 사용하지만 쓰기 비용이 증가합니다.")),
                "lenient",
                "지원자");

        assertThat(sut.evaluate(request)).isEqualTo(sut.evaluate(request));
    }

    private RawEvaluationResponse evaluateOne(String answer, String expectedAnswer) {
        return sut.evaluate(new EvaluationRequest(
                List.of(new QuestionAnswerPair(1, "인덱스를 설명해 주세요.", answer, expectedAnswer)),
                "strict",
                "지원자"));
    }

    private QuestionAnswerPair pair(int questionId, String answer) {
        return new QuestionAnswerPair(
                questionId,
                "인덱스를 설명해 주세요.",
                answer,
                "카디널리티 where join 조회 성능");
    }
}
