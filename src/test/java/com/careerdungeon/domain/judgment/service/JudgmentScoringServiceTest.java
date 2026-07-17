package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawQuestionEvaluation;
import com.careerdungeon.domain.judgment.llm.dto.RubricScores;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 최초 확정 점수 유지, turn 4 단독 채점, 최종 합산과 스키마 방어를 검증한다. */
class JudgmentScoringServiceTest {

    private final JudgmentScoringService sut = new JudgmentScoringService(candidates -> candidates.get(0));

    /** LLM 보고 점수 대신 turn 4 루브릭 clamp 합계가 확정 점수가 되는지 확인한다. */
    @ParameterizedTest(name = "LLM 보고 문항 점수 {0}, 서버 확정 점수 {2}")
    @MethodSource("questionScoreBoundaries")
    @DisplayName("turn 4 문항 점수 -1, 0, 25, 26 경계에서 루브릭 clamp 합계를 신뢰한다")
    void clampsFollowUpRubricAndRecalculatesQuestionScore(
            int reportedScore, RubricScores rawRubric, int expected) {
        FinalJudgmentEvaluation result = sut.scoreFinal(
                initialEvaluation(0, 0, 0),
                finalResponse(List.of(question(4, reportedScore, rawRubric)), reportedScore));

        assertThat(result.evaluations()).hasSize(4);
        assertThat(result.evaluations().get(3).score()).isEqualTo(expected);
    }

    /** 문항 점수 하한·정상 상한·상한 초과 입력을 제공한다. */
    static Stream<Arguments> questionScoreBoundaries() {
        return Stream.of(
                Arguments.of(-1, new RubricScores(-1, -1, -1, -1, -1), 0),
                Arguments.of(0, new RubricScores(0, 0, 0, 0, 0), 0),
                Arguments.of(25, new RubricScores(10, 5, 4, 3, 3), 25),
                Arguments.of(26, new RubricScores(11, 6, 5, 4, 4), 25));
    }

    /** 최초 확정 점수와 turn 4 점수를 합쳐 최종 합격 경계를 판정하는지 확인한다. */
    @ParameterizedTest(name = "총점 {0} => passed={1}")
    @MethodSource("totalScoreBoundaries")
    @DisplayName("최종 총점 79, 80, 100과 LLM 원시 총점 101 경계를 서버 합산으로 방어한다")
    void combinesInitialScoresAndFollowUp(int expectedTotal, boolean expectedPassed,
                                          int reportedTotal, int first, int second,
                                          int third, int followUp) {
        FinalJudgmentEvaluation result = sut.scoreFinal(
                initialEvaluation(first, second, third),
                finalResponse(List.of(question(4, followUp, rubricFor(followUp))), reportedTotal));

        assertThat(result.totalScore()).isEqualTo(expectedTotal);
        assertThat(result.passed()).isEqualTo(expectedPassed);
        assertThat(result.evaluations()).extracting(QuestionScore::questionId)
                .containsExactly(1, 2, 3, 4);
    }

    /** 불합격·합격·만점·상한 초과 보고값 사례를 제공한다. */
    static Stream<Arguments> totalScoreBoundaries() {
        return Stream.of(
                Arguments.of(79, false, 80, 25, 25, 25, 4),
                Arguments.of(80, true, 79, 25, 25, 25, 5),
                Arguments.of(100, true, 100, 25, 25, 25, 25),
                Arguments.of(100, true, 101, 25, 25, 25, 25));
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

        InitialJudgmentEvaluation result = tieAwareService.scoreInitial(response);

        assertThat(candidatesSeen.get()).containsExactly(1, 2);
        assertThat(result.weakestQuestionId()).isEqualTo(2);
    }

    /** turn 4의 선택 호환 score 필드가 없어도 루브릭 합계로 채점되는지 확인한다. */
    @Test
    @DisplayName("turn 4의 LLM 보고 score가 누락돼도 루브릭으로 재계산한다")
    void acceptsMissingReportedScore() {
        RawFinalEvaluationResponse response = finalResponse(
                List.of(question(4, null, rubricFor(20))), 0);

        FinalJudgmentEvaluation result = sut.scoreFinal(initialEvaluation(20, 20, 20), response);

        assertThat(result.evaluations().get(3).score()).isEqualTo(20);
        assertThat(result.totalScore()).isEqualTo(80);
    }

    /** turn 4 루브릭 숫자 하나가 null이면 스키마 오류가 나는지 확인한다. */
    @Test
    @DisplayName("turn 4의 5개 루브릭 중 하나라도 누락되면 스키마 오류로 거부한다")
    void rejectsMissingRubricField() {
        RawFinalEvaluationResponse response = finalResponse(List.of(
                question(4, 20, new RubricScores(8, 4, null, 3, 3))), 20);

        assertThatThrownBy(() -> sut.scoreFinal(initialEvaluation(20, 20, 20), response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("rubricScores");
    }

    /** 최초·최종 타입별 필수 상위 필드가 검증되는지 확인한다. */
    @Test
    @DisplayName("최초 응답의 weakestQuestionId가 누락되면 스키마 오류로 거부한다")
    void rejectsMissingInitialRequiredField() {
        RawInitialEvaluationResponse response = new RawInitialEvaluationResponse(
                initialQuestions(), 60, null, false);

        assertThatThrownBy(() -> sut.scoreInitial(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("weakestQuestionId");
    }

    /** 최종 응답은 꼬리질문 반영 종합 피드백을 필수로 요구한다. */
    @Test
    @DisplayName("최종 응답의 overallFeedback이 누락되면 스키마 오류로 거부한다")
    void rejectsMissingFinalOverallFeedback() {
        RawFinalEvaluationResponse response = new RawFinalEvaluationResponse(
                List.of(question(4, 20, rubricFor(20))), 20, false, " ");

        assertThatThrownBy(() -> sut.scoreFinal(initialEvaluation(20, 20, 20), response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("overallFeedback");
    }

    /** LLM 파생 판정이 틀려도 서버가 최초 최저점과 최종 합격 여부를 덮어쓰는지 확인한다. */
    @Test
    @DisplayName("LLM 파생값이 틀려도 서버가 최초 최저점과 최종 passed를 다시 판정한다")
    void ignoresLlmDerivedJudgments() {
        RawInitialEvaluationResponse initialRaw = new RawInitialEvaluationResponse(
                List.of(
                        question(1, 25, rubricFor(25)),
                        question(2, 5, rubricFor(5)),
                        question(3, 25, rubricFor(25))),
                0,
                1,
                true);
        InitialJudgmentEvaluation initial = sut.scoreInitial(initialRaw);
        RawFinalEvaluationResponse finalRaw = new RawFinalEvaluationResponse(
                List.of(question(4, 25, rubricFor(25))),
                0,
                false,
                "종합 피드백");

        assertThat(initial.weakestQuestionId()).isEqualTo(2);
        assertThat(sut.scoreFinal(initial, finalRaw).passed()).isTrue();
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

    /** 최종 LLM 응답 문항 집합은 정확히 questionId 4 한 건이어야 한다. */
    @Test
    @DisplayName("최종 응답 문항 구성이 questionId 4 한 건이 아니면 거부한다")
    void rejectsWrongFinalQuestionSet() {
        RawFinalEvaluationResponse response = finalResponse(List.of(
                question(3, 20, rubricFor(20)),
                question(4, 20, rubricFor(20))), 40);

        assertThatThrownBy(() -> sut.scoreFinal(initialEvaluation(20, 20, 20), response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("문항 구성");
    }

    /** 저장 후 로드한 최초 확정 점수도 1~3 구성과 점수 범위를 방어한다. */
    @Test
    @DisplayName("최초 확정 점수는 1~3 구성만 허용하고 범위 밖 점수는 clamp한다")
    void validatesAndClampsStoredInitialScores() {
        InitialJudgmentEvaluation outOfRange = new InitialJudgmentEvaluation(List.of(
                new QuestionScore(3, 26, "피드백3"),
                new QuestionScore(1, -1, "피드백1"),
                new QuestionScore(2, 25, "피드백2")), 50, 1, false);

        FinalJudgmentEvaluation result = sut.scoreFinal(
                outOfRange,
                finalResponse(List.of(question(4, 25, rubricFor(25))), 25));

        assertThat(result.evaluations()).extracting(QuestionScore::score)
                .containsExactly(0, 25, 25, 25);
        assertThat(result.evaluations()).extracting(QuestionScore::questionId)
                .containsExactly(1, 2, 3, 4);
        assertThat(result.totalScore()).isEqualTo(75);
    }

    /** 최초 확정 점수가 누락되거나 중복되면 합산하지 않는다. */
    @Test
    @DisplayName("최초 확정 점수의 문항이 누락되거나 중복되면 거부한다")
    void rejectsInvalidStoredInitialComposition() {
        InitialJudgmentEvaluation invalid = new InitialJudgmentEvaluation(List.of(
                new QuestionScore(1, 20, "피드백1"),
                new QuestionScore(1, 20, "피드백1 중복"),
                new QuestionScore(3, 20, "피드백3")), 60, 1, false);

        assertThatThrownBy(() -> sut.scoreFinal(
                invalid,
                finalResponse(List.of(question(4, 20, rubricFor(20))), 20)))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("1,2,3");
    }

    /** 새로 채점한 꼬리질문의 피드백은 반드시 있어야 한다. */
    @Test
    @DisplayName("최종 응답에서 questionId 4의 feedback이 없으면 거부한다")
    void rejectsMissingFollowUpFeedback() {
        RawFinalEvaluationResponse response = finalResponse(List.of(
                question(4, 20, rubricFor(20), null)), 20);

        assertThatThrownBy(() -> sut.scoreFinal(initialEvaluation(20, 20, 20), response))
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
    /** 최종 합계와 합격 여부가 서로 다른 상태로 생성되지 않도록 모델 경계에서 차단한다. */
    @Test
    @DisplayName("최종 점수와 합격 여부가 일치하지 않으면 거부한다")
    void finalResultRejectsInconsistentPassedFlag() {
        assertThatThrownBy(() -> new FinalJudgmentEvaluation(
                List.of(new QuestionScore(4, 20, "피드백")),
                80,
                false,
                "종합 피드백"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("합격 여부");
    }

    private static RawInitialEvaluationResponse initialResponse(
            List<RawQuestionEvaluation> evaluations, int reportedTotal) {
        return new RawInitialEvaluationResponse(evaluations, reportedTotal, 1, false);
    }

    /** 최초 확정 점수 스냅샷을 생성한다. */
    private static InitialJudgmentEvaluation initialEvaluation(int first, int second, int third) {
        return new InitialJudgmentEvaluation(List.of(
                new QuestionScore(1, first, "피드백1"),
                new QuestionScore(2, second, "피드백2"),
                new QuestionScore(3, third, "피드백3")),
                first + second + third,
                1,
                false);
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

    /** 문항 식별자·보고 점수·루브릭으로 테스트용 원시 평가를 생성한다. */
    private static RawQuestionEvaluation question(int questionId, Integer reportedScore, RubricScores rubric) {
        return question(questionId, reportedScore, rubric, "문항 피드백");
    }

    /** 피드백 선택 여부까지 지정해 테스트용 원시 평가를 생성한다. */
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
