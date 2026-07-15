package com.careerdungeon.domain.judgment.llm.mock;

import com.careerdungeon.domain.judgment.llm.dto.EvaluationRequest;
import com.careerdungeon.domain.judgment.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.domain.judgment.llm.dto.QuestionAnswerPair;
import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 모범답변 비교 기반 Mock 채점의 단계별 정상·실패 동작을 검증한다. */
class MockEvaluationLlmClientTest {

    private final MockEvaluationLlmClient sut = new MockEvaluationLlmClient();

    /** 모범답변이 달라지면 동일 답변의 핵심 토큰 충족 점수도 달라지는지 확인한다. */
    @Test
    @DisplayName("동일한 사용자 답변도 모범답변의 핵심어 충족도에 따라 점수가 달라진다")
    void expectedAnswerChangesScore() {
        String answer = "인덱스는 카디널리티가 높은 컬럼의 where와 join 조회 성능을 개선합니다.";

        int matchingScore = evaluateFirst(answer,
                "카디널리티가 높은 컬럼, where join 조회 성능 개선").evaluations().get(0).score();
        int unrelatedScore = evaluateFirst(answer,
                "가비지 컬렉션 stop the world young old generation").evaluations().get(0).score();

        assertThat(matchingScore).isGreaterThan(unrelatedScore);
    }

    /** 공백 답변이 모든 루브릭에서 0점으로 처리되는지 확인한다. */
    @Test
    @DisplayName("무응답은 5개 루브릭과 문항 점수가 모두 0점이다")
    void blankAnswerScoresZero() {
        var evaluation = evaluateFirst("  ", "인덱스 카디널리티 조회 성능").evaluations().get(0);

        assertThat(evaluation.score()).isZero();
        assertThat(evaluation.rubricScores().technicalAccuracy()).isZero();
        assertThat(evaluation.rubricScores().coreCoverage()).isZero();
        assertThat(evaluation.rubricScores().reasoning()).isZero();
        assertThat(evaluation.rubricScores().specificity()).isZero();
        assertThat(evaluation.rubricScores().tradeOffsAndExceptions()).isZero();
    }

    /** 최초 채점이 세 문항과 최저점 식별자를 반환하는지 확인한다. */
    @Test
    @DisplayName("최초 채점은 questionId 1~3과 최저점 문항을 반환한다")
    void initialEvaluationReturnsThreeQuestions() {
        RawInitialEvaluationResponse response = sut.evaluateInitial(new EvaluationRequest(
                List.of(pair(1, "답변 1"), pair(2, "답변 2"), pair(3, "답변 3")),
                "strict",
                "최용성"));

        assertThat(response.evaluations()).extracting(evaluation -> evaluation.questionId())
                .containsExactly(1, 2, 3);
        assertThat(response.weakestQuestionId()).isIn(1, 2, 3);
        assertThat(response.passed()).isFalse();
    }

    /** 최종 채점이 turn 4의 5개 루브릭 숫자와 종합 피드백을 반환하는지 확인한다. */
    @Test
    @DisplayName("최종 채점은 turn 4 한 문항의 5개 루브릭 숫자와 종합 피드백을 반환한다")
    void finalEvaluationReturnsOnlyFollowUpQuestion() {
        RawFinalEvaluationResponse response = sut.evaluateFinal(new EvaluationRequest(
                List.of(pair(4, "답변 4")),
                "strict",
                "최용성",
                previousContexts()));

        assertThat(response.evaluations()).hasSize(1).allSatisfy(evaluation -> {
            assertThat(evaluation.questionId()).isEqualTo(4);
            assertThat(evaluation.score()).isBetween(0, 25);
            assertThat(evaluation.rubricScores().technicalAccuracy()).isNotNull();
            assertThat(evaluation.rubricScores().coreCoverage()).isNotNull();
            assertThat(evaluation.rubricScores().reasoning()).isNotNull();
            assertThat(evaluation.rubricScores().specificity()).isNotNull();
            assertThat(evaluation.rubricScores().tradeOffsAndExceptions()).isNotNull();
        });
        assertThat(response.overallFeedback()).contains("최용성");
        assertThat(response.overallFeedback()).contains("questionId=2", "피드백 2");
    }

    /** 최종 피드백의 근거가 되는 최초 1~3 평가 컨텍스트가 없으면 요청을 거부한다. */
    @Test
    @DisplayName("최종 채점은 최초 1~3 평가 컨텍스트를 모두 요구한다")
    void rejectsMissingPreviousEvaluationContext() {
        EvaluationRequest request = new EvaluationRequest(
                List.of(pair(4, "답변 4")), "strict", "지원자", List.of());

        assertThatThrownBy(() -> sut.evaluateFinal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("컨텍스트 3건");
    }

    /** 외부 난수나 시간에 의존하지 않고 동일 요청이 동일 원시 응답을 만드는지 확인한다. */
    @Test
    @DisplayName("같은 최종 요청은 항상 같은 원시 응답을 반환한다")
    void evaluationIsDeterministic() {
        EvaluationRequest request = new EvaluationRequest(
                List.of(pair(4, "인덱스는 조회 성능 때문에 사용하지만 쓰기 비용이 증가합니다.")),
                "lenient",
                "지원자",
                previousContexts());

        assertThat(sut.evaluateFinal(request)).isEqualTo(sut.evaluateFinal(request));
    }

    /** 중복 식별자는 재시도 가능한 LLM 응답 오류가 아니라 잘못된 내부 요청으로 거부한다. */
    @Test
    @DisplayName("중복 questionId가 있으면 입력 오류로 거부한다")
    void rejectsDuplicateQuestionId() {
        EvaluationRequest request = new EvaluationRequest(
                List.of(pair(1, "답변"), pair(1, "답변"), pair(3, "답변")), "strict", "지원자");

        assertThatThrownBy(() -> sut.evaluateInitial(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    /** 문항 ID 범위 이탈을 검증한다. */
    @Test
    @DisplayName("0 이하 questionId가 있으면 입력 오류로 거부한다")
    void rejectsNonPositiveQuestionId() {
        EvaluationRequest request = new EvaluationRequest(
                List.of(pair(0, "답변"), pair(2, "답변"), pair(3, "답변")), "strict", "지원자");

        assertThatThrownBy(() -> sut.evaluateInitial(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("양수");
    }

    /** 단계에 필요한 문항 집합이 정확히 일치하는지 검증한다. */
    @Test
    @DisplayName("최종 채점에 questionId 4 한 건이 아니면 입력 오류로 거부한다")
    void rejectsWrongFinalQuestionSet() {
        EvaluationRequest request = new EvaluationRequest(
                List.of(pair(3, "답변"), pair(4, "답변")), "strict", "지원자", previousContexts());

        assertThatThrownBy(() -> sut.evaluateFinal(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최종 채점 문항 구성");
    }

    /** 질문 또는 모범답변이 비어 있는 내부 요청을 검증한다. */
    @Test
    @DisplayName("질문이나 모범답변이 비어 있으면 입력 오류로 거부한다")
    void rejectsBlankQuestionOrExpectedAnswer() {
        EvaluationRequest blankQuestionRequest = new EvaluationRequest(
                List.of(
                        new QuestionAnswerPair(1, " ", "답변", "모범답변"),
                        pair(2, "답변"),
                        pair(3, "답변")),
                "strict",
                "지원자");
        EvaluationRequest blankExpectedAnswerRequest = new EvaluationRequest(
                List.of(
                        new QuestionAnswerPair(1, "질문", "답변", " "),
                        pair(2, "답변"),
                        pair(3, "답변")),
                "strict",
                "지원자");

        assertThatThrownBy(() -> sut.evaluateInitial(blankQuestionRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수");
        assertThatThrownBy(() -> sut.evaluateInitial(blankExpectedAnswerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수");
    }

    /** 첫 문항의 비교 조건만 바꾸고 최초 세 문항 요청을 완성한다. */
    private RawInitialEvaluationResponse evaluateFirst(String answer, String expectedAnswer) {
        return sut.evaluateInitial(new EvaluationRequest(
                List.of(
                        new QuestionAnswerPair(1, "인덱스를 설명해 주세요.", answer, expectedAnswer),
                        pair(2, "답변 2"),
                        pair(3, "답변 3")),
                "strict",
                "지원자"));
    }

    /** 지정한 문항 번호와 답변으로 공통 질문-답변 쌍을 생성한다. */
    private QuestionAnswerPair pair(int questionId, String answer) {
        return new QuestionAnswerPair(
                questionId,
                "인덱스를 설명해 주세요.",
                answer,
                "카디널리티 where join 조회 성능");
    }

    /** 최초 1~3번 채점 결과를 최종 피드백용 읽기 전용 컨텍스트로 구성한다. */
    private List<PreviousEvaluationContext> previousContexts() {
        return List.of(
                new PreviousEvaluationContext(1, "질문 1", "답변 1", 20, "피드백 1"),
                new PreviousEvaluationContext(2, "질문 2", "답변 2", 10, "피드백 2"),
                new PreviousEvaluationContext(3, "질문 3", "답변 3", 15, "피드백 3"));
    }
}
