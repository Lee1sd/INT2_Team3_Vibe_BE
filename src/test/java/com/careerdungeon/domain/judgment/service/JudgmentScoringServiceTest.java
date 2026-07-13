package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawQuestionEvaluation;
import com.careerdungeon.domain.judgment.llm.dto.RubricScores;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 단계별 LLM 원시값 검증, 루브릭 clamp, 서버 파생값 재계산 정책을 검증한다. */
class JudgmentScoringServiceTest {

    private final JudgmentScoringService sut = new JudgmentScoringService(candidates -> candidates.get(0));

    /** LLM 보고 점수 대신 항목별 clamp 합계가 확정 점수가 되는지 경계값으로 확인한다. */
    @ParameterizedTest(name = "LLM 보고 문항 점수 {0}, 서버 확정 점수 {2}")
    @MethodSource("questionScoreBoundaries")
    @DisplayName("문항 점수 -1, 0, 25, 26 경계에서도 5개 항목 clamp 합계를 신뢰한다")
    void clampsEachRubricAndRecalculatesQuestionScore(int reportedScore, RubricScores rawRubric, int expected) {
        List<RawQuestionEvaluation> evaluations = List.of(
                question(1, reportedScore, rawRubric),
                question(2, 0, rubricFor(0)),
                question(3, 0, rubricFor(0)),
                question(4, 0, rubricFor(0)));

        assertThat(sut.scoreFinal(finalResponse(evaluations, reportedScore)).evaluations().get(0).score())
                .isEqualTo(expected);
    }

    /** 문항 점수 하한·정상 상한·상한 초과 입력을 제공한다. */
    static Stream<Arguments> questionScoreBoundaries() {
        return Stream.of(
                Arguments.of(-1, new RubricScores(-1, -1, -1, -1, -1), 0),
                Arguments.of(0, new RubricScores(0, 0, 0, 0, 0), 0),
                Arguments.of(25, new RubricScores(10, 5, 4, 3, 3), 25),
                Arguments.of(26, new RubricScores(11, 6, 5, 4, 4), 25));
    }

    /** 최종 4문항 총점 80점 경계와 원시 총점 101점 보고값을 확인한다. */
    @ParameterizedTest(name = "총점 {0} => passed={1}")
    @MethodSource("totalScoreBoundaries")
    @DisplayName("서버 재계산 총점 79, 80, 100과 LLM 원시 총점 101 경계를 방어한다")
    void recalculatesTotalAndPassed(int expectedTotal, boolean expectedPassed, int reportedTotal, int... questionScores) {
        List<RawQuestionEvaluation> evaluations = new ArrayList<>();
        for (int index = 0; index < questionScores.length; index++) {
            evaluations.add(question(index + 1, questionScores[index], rubricFor(questionScores[index])));
        }

        var result = sut.scoreFinal(finalResponse(evaluations, reportedTotal));

        assertThat(result.totalScore()).isEqualTo(expectedTotal);
        assertThat(result.passed()).isEqualTo(expectedPassed);
    }

    /** 불합격·합격·만점·상한 초과 보고값에 사용할 네 문항 총점 사례를 제공한다. */
    static Stream<Arguments> totalScoreBoundaries() {
        return Stream.of(
                Arguments.of(79, false, 80, new int[]{25, 25, 25, 4}),
                Arguments.of(80, true, 79, new int[]{25, 25, 25, 5}),
                Arguments.of(100, true, 100, new int[]{25, 25, 25, 25}),
                Arguments.of(100, true, 101, new int[]{25, 25, 25, 25}));
    }

    /** 최초 세 문항의 최저점 동점 후보가 선택 전략에 모두 전달되는지 확인한다. */
    @Test
    @DisplayName("최초 채점의 최저점 동점 후보 전체를 주입된 선택 전략에 전달한다")
    void delegatesWeakestTieToInjectedStrategy() {
        AtomicReference<List<Integer>> candidatesSeen = new AtomicReference<>();
        JudgmentScoringService tieAwareService = new JudgmentScoringService(candidates -> {
            candidatesSeen.set(candidates);
            return candidates.get(1);
        });
        RawInitialEvaluationResponse response = initialResponse(List.of(
                question(1, 10, rubricFor(10)),
                question(2, 10, rubricFor(10)),
                question(3, 20, rubricFor(20))), 40);

        var result = tieAwareService.scoreInitial(response);

        assertThat(candidatesSeen.get()).containsExactly(1, 2);
        assertThat(result.weakestQuestionId()).isEqualTo(2);
    }

    /** 선택 호환 필드인 score가 없어도 루브릭 합계로 정상 채점되는지 확인한다. */
    @Test
    @DisplayName("LLM 보고 score가 누락돼도 루브릭으로 문항 점수를 재계산한다")
    void acceptsMissingReportedScore() {
        RawFinalEvaluationResponse response = finalResponse(List.of(
                question(1, null, rubricFor(25)),
                question(2, 20, rubricFor(20)),
                question(3, 20, rubricFor(20)),
                question(4, 15, rubricFor(15))), 0);

        var result = sut.scoreFinal(response);

        assertThat(result.evaluations().get(0).score()).isEqualTo(25);
        assertThat(result.totalScore()).isEqualTo(80);
    }

    /** 루브릭 숫자 하나가 null이면 기본값을 쓰지 않고 스키마 오류가 나는지 확인한다. */
    @Test
    @DisplayName("5개 루브릭 중 하나라도 누락되면 스키마 오류로 거부한다")
    void rejectsMissingRubricField() {
        RawFinalEvaluationResponse response = finalResponse(List.of(
                question(1, 20, new RubricScores(8, 4, null, 3, 3)),
                question(2, 20, rubricFor(20)),
                question(3, 20, rubricFor(20)),
                question(4, 20, rubricFor(20))), 80);

        assertThatThrownBy(() -> sut.scoreFinal(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("rubricScores");
    }

    /** 최초·최종 타입별 필수 상위 필드가 다르게 검증되는지 확인한다. */
    @Test
    @DisplayName("최초 응답의 weakestQuestionId가 누락되면 스키마 오류로 거부한다")
    void rejectsMissingInitialRequiredField() {
        RawInitialEvaluationResponse response = new RawInitialEvaluationResponse(
                initialQuestions(), 60, null, false);

        assertThatThrownBy(() -> sut.scoreInitial(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("weakestQuestionId");
    }

    /** 최종 응답은 종합 피드백을 필수로 요구한다. */
    @Test
    @DisplayName("최종 응답의 overallFeedback이 누락되면 스키마 오류로 거부한다")
    void rejectsMissingFinalOverallFeedback() {
        RawFinalEvaluationResponse response = new RawFinalEvaluationResponse(
                finalQuestions(20, 20, 20, 20), 80, true, " ");

        assertThatThrownBy(() -> sut.scoreFinal(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("overallFeedback");
    }

    /** LLM 파생 판정이 틀려도 최초 최저점과 최종 합격 여부를 서버가 덮어쓰는지 확인한다. */
    @Test
    @DisplayName("LLM 파생값이 틀려도 서버가 최초 최저점과 최종 passed를 다시 판정한다")
    void ignoresLlmDerivedJudgments() {
        RawInitialEvaluationResponse initial = new RawInitialEvaluationResponse(
                List.of(
                        question(1, 25, rubricFor(25)),
                        question(2, 5, rubricFor(5)),
                        question(3, 25, rubricFor(25))),
                0,
                1,
                true);
        RawFinalEvaluationResponse finalResponse = new RawFinalEvaluationResponse(
                finalQuestions(25, 5, 25, 25),
                0,
                false,
                "종합 피드백");

        assertThat(sut.scoreInitial(initial).weakestQuestionId()).isEqualTo(2);
        assertThat(sut.scoreFinal(finalResponse).passed()).isTrue();
    }

    /** 최초 채점 문항 집합은 정확히 questionId 1~3이어야 한다. */
    @Test
    @DisplayName("최초 응답 문항 구성이 1,2,3이 아니면 스키마 오류로 거부한다")
    void rejectsWrongInitialQuestionSet() {
        RawInitialEvaluationResponse response = initialResponse(List.of(
                question(1, 20, rubricFor(20)),
                question(2, 20, rubricFor(20)),
                question(4, 20, rubricFor(20))), 60);

        assertThatThrownBy(() -> sut.scoreInitial(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("문항 구성");
    }

    /** 최종 채점 문항 집합은 정확히 questionId 1~4여야 한다. */
    @Test
    @DisplayName("최종 응답 문항 구성이 1,2,3,4가 아니면 스키마 오류로 거부한다")
    void rejectsWrongFinalQuestionSet() {
        RawFinalEvaluationResponse response = finalResponse(List.of(
                question(1, 20, rubricFor(20)),
                question(2, 20, rubricFor(20)),
                question(3, 20, rubricFor(20))), 60);

        assertThatThrownBy(() -> sut.scoreFinal(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("문항 구성");
    }

    /** 최종 응답의 기존 세 문항 피드백은 선택값으로 허용한다. */
    @Test
    @DisplayName("최종 응답에서 questionId 1~3의 feedback은 생략할 수 있다")
    void allowsMissingRetainedFeedbackInFinalResponse() {
        RawFinalEvaluationResponse response = finalResponse(List.of(
                question(1, 20, rubricFor(20), null),
                question(2, 20, rubricFor(20), null),
                question(3, 20, rubricFor(20), null),
                question(4, 20, rubricFor(20), "꼬리질문 피드백")), 80);

        assertThat(sut.scoreFinal(response).totalScore()).isEqualTo(80);
    }

    /** 새로 채점한 꼬리질문의 피드백은 반드시 있어야 한다. */
    @Test
    @DisplayName("최종 응답에서 questionId 4의 feedback이 없으면 거부한다")
    void rejectsMissingFollowUpFeedback() {
        RawFinalEvaluationResponse response = finalResponse(List.of(
                question(1, 20, rubricFor(20)),
                question(2, 20, rubricFor(20)),
                question(3, 20, rubricFor(20)),
                question(4, 20, rubricFor(20), null)), 80);

        assertThatThrownBy(() -> sut.scoreFinal(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("questionId=4");
    }

    /** 결과 record가 null 평가 목록을 명확한 메시지로 거부하는지 확인한다. */
    @Test
    @DisplayName("초기·최종 결과는 null 평가 목록을 명확하게 거부한다")
    void resultModelsRejectNullEvaluations() {
        assertThatThrownBy(() -> new InitialJudgmentEvaluation(null, 0, 1, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("최초 평가 목록");
        assertThatThrownBy(() -> new FinalJudgmentEvaluation(null, 0, false, "피드백"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("최종 평가 목록");
    }

    /** 공통 상위 필드를 채운 최초 원시 평가 응답을 생성한다. */
    private static RawInitialEvaluationResponse initialResponse(
            List<RawQuestionEvaluation> evaluations, int reportedTotal) {
        return new RawInitialEvaluationResponse(evaluations, reportedTotal, 1, false);
    }

    /** 공통 상위 필드를 채운 최종 원시 평가 응답을 생성한다. */
    private static RawFinalEvaluationResponse finalResponse(
            List<RawQuestionEvaluation> evaluations, int reportedTotal) {
        return new RawFinalEvaluationResponse(evaluations, reportedTotal, false, "종합 피드백");
    }

    /** 정상 최초 세 문항을 생성한다. */
    private static List<RawQuestionEvaluation> initialQuestions() {
        return List.of(
                question(1, 20, rubricFor(20)),
                question(2, 20, rubricFor(20)),
                question(3, 20, rubricFor(20)));
    }

    /** 지정 점수로 정상 최종 네 문항을 생성한다. */
    private static List<RawQuestionEvaluation> finalQuestions(int first, int second, int third, int fourth) {
        return List.of(
                question(1, first, rubricFor(first)),
                question(2, second, rubricFor(second)),
                question(3, third, rubricFor(third)),
                question(4, fourth, rubricFor(fourth)));
    }

    /** 문항 식별자·보고 점수·루브릭으로 테스트용 원시 문항 평가를 생성한다. */
    private static RawQuestionEvaluation question(int questionId, Integer reportedScore, RubricScores rubric) {
        return question(questionId, reportedScore, rubric, "문항 피드백");
    }

    /** 피드백 선택 여부까지 지정해 테스트용 원시 문항 평가를 생성한다. */
    private static RawQuestionEvaluation question(
            int questionId, Integer reportedScore, RubricScores rubric, String feedback) {
        return new RawQuestionEvaluation(questionId, reportedScore, rubric, feedback);
    }

    /** 원하는 0~25점 합계를 루브릭 배점 순서로 분배한다. */
    private static RubricScores rubricFor(int score) {
        int remaining = Math.max(0, score);
        int technical = Math.min(10, remaining);
        remaining -= technical;
        int coverage = Math.min(5, remaining);
        remaining -= coverage;
        int reasoning = Math.min(4, remaining);
        remaining -= reasoning;
        int specificity = Math.min(3, remaining);
        remaining -= specificity;
        int tradeOff = Math.min(3, remaining);
        return new RubricScores(technical, coverage, reasoning, specificity, tradeOff);
    }
}
