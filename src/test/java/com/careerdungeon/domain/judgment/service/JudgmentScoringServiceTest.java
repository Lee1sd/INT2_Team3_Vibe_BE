package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.judgment.llm.dto.RawEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawQuestionEvaluation;
import com.careerdungeon.domain.judgment.llm.dto.RubricScores;
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

/** LLM 원시값 검증, 루브릭 clamp, 서버 파생값 재계산 정책을 검증한다. */
class JudgmentScoringServiceTest {

    private final JudgmentScoringService sut = new JudgmentScoringService(candidates -> candidates.get(0));

    /** LLM 보고 점수 대신 항목별 clamp 합계가 문항 확정 점수가 되는지 경계값으로 확인한다. */
    @ParameterizedTest(name = "LLM 보고 문항 점수 {0}, 서버 확정 점수 {2}")
    @MethodSource("questionScoreBoundaries")
    @DisplayName("문항 점수 -1, 0, 25, 26 경계에서도 5개 항목 clamp 합계를 신뢰한다")
    void clampsEachRubricAndRecalculatesQuestionScore(int reportedScore, RubricScores rawRubric, int expected) {
        RawEvaluationResponse response = response(List.of(question(1, reportedScore, rawRubric)), reportedScore);

        assertThat(sut.score(response).evaluations().get(0).score()).isEqualTo(expected);
    }

    /** 문항 점수 하한·정상 상한·상한 초과 입력을 제공한다. */
    static Stream<Arguments> questionScoreBoundaries() {
        return Stream.of(
                Arguments.of(-1, new RubricScores(-1, -1, -1, -1, -1), 0),
                Arguments.of(0, new RubricScores(0, 0, 0, 0, 0), 0),
                Arguments.of(25, new RubricScores(10, 5, 4, 3, 3), 25),
                Arguments.of(26, new RubricScores(11, 6, 5, 4, 4), 25));
    }

    /** 총점 80점 경계와 원시 총점 101점 입력에서도 서버 판정이 유지되는지 확인한다. */
    @ParameterizedTest(name = "총점 {0} => passed={1}")
    @MethodSource("totalScoreBoundaries")
    @DisplayName("서버 재계산 총점 79, 80, 100과 LLM 원시 총점 101 경계를 방어한다")
    void recalculatesTotalAndPassed(int expectedTotal, boolean expectedPassed, int reportedTotal, int... questionScores) {
        List<RawQuestionEvaluation> evaluations = new ArrayList<>();
        for (int index = 0; index < questionScores.length; index++) {
            evaluations.add(question(index + 1, questionScores[index], rubricFor(questionScores[index])));
        }

        var result = sut.score(response(evaluations, reportedTotal));

        assertThat(result.totalScore()).isEqualTo(expectedTotal);
        assertThat(result.passed()).isEqualTo(expectedPassed);
    }

    /** 불합격·합격·만점·상한 초과 보고값에 사용할 총점 사례를 제공한다. */
    static Stream<Arguments> totalScoreBoundaries() {
        return Stream.of(
                Arguments.of(79, false, 80, new int[]{25, 25, 25, 4}),
                Arguments.of(80, true, 79, new int[]{25, 25, 25, 5}),
                Arguments.of(100, true, 100, new int[]{25, 25, 25, 25}),
                Arguments.of(100, true, 101, new int[]{25, 25, 25, 25}));
    }

    /** 최저점 동점 후보가 누락 없이 선택 전략에 전달되는지 확인한다. */
    @Test
    @DisplayName("최저점 동점 후보 전체를 주입된 선택 전략에 전달한다")
    void delegatesWeakestTieToInjectedStrategy() {
        AtomicReference<List<Integer>> candidatesSeen = new AtomicReference<>();
        JudgmentScoringService tieAwareService = new JudgmentScoringService(candidates -> {
            candidatesSeen.set(candidates);
            return candidates.get(1);
        });
        RawEvaluationResponse response = response(List.of(
                question(1, 10, rubricFor(10)),
                question(2, 10, rubricFor(10)),
                question(3, 20, rubricFor(20))), 40);

        var result = tieAwareService.score(response);

        assertThat(candidatesSeen.get()).containsExactly(1, 2);
        assertThat(result.weakestQuestionId()).isEqualTo(2);
    }

    /** 루브릭 숫자 하나가 null이면 조용히 기본값을 쓰지 않고 스키마 오류가 나는지 확인한다. */
    @Test
    @DisplayName("5개 루브릭 중 하나라도 누락되면 스키마 오류로 거부한다")
    void rejectsMissingRubricField() {
        RawEvaluationResponse response = response(List.of(
                question(1, 20, new RubricScores(8, 4, null, 3, 3))), 20);

        assertThatThrownBy(() -> sut.score(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("rubricScores");
    }

    /** totalScore 등 필수 상위 필드가 누락되면 스키마 오류로 차단하는지 확인한다. */
    @Test
    @DisplayName("필수 상위 평가 필드가 누락되면 스키마 오류로 거부한다")
    void rejectsMissingRequiredTopLevelField() {
        RawEvaluationResponse response = new RawEvaluationResponse(
                List.of(question(1, 20, rubricFor(20))),
                null,
                1,
                false,
                "피드백");

        assertThatThrownBy(() -> sut.score(response))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("필수 필드");
    }

    /** LLM 파생 판정이 실제 루브릭 합계와 달라도 서버가 올바르게 덮어쓰는지 확인한다. */
    @Test
    @DisplayName("LLM의 weakestQuestionId와 passed가 틀려도 서버가 다시 판정한다")
    void ignoresLlmDerivedJudgments() {
        RawEvaluationResponse response = new RawEvaluationResponse(
                List.of(
                        question(1, 25, rubricFor(25)),
                        question(2, 5, rubricFor(5)),
                        question(3, 25, rubricFor(25)),
                        question(4, 25, rubricFor(25))),
                0,
                1,
                false,
                "종합 피드백");

        var result = sut.score(response);

        assertThat(result.totalScore()).isEqualTo(80);
        assertThat(result.weakestQuestionId()).isEqualTo(2);
        assertThat(result.passed()).isTrue();
    }

    /** 공통 상위 필드를 채운 원시 평가 응답을 생성한다. */
    private static RawEvaluationResponse response(List<RawQuestionEvaluation> evaluations, int reportedTotal) {
        return new RawEvaluationResponse(evaluations, reportedTotal, 1, false, "종합 피드백");
    }

    /** 문항 식별자·보고 점수·루브릭으로 테스트용 원시 문항 평가를 생성한다. */
    private static RawQuestionEvaluation question(int questionId, int reportedScore, RubricScores rubric) {
        return new RawQuestionEvaluation(questionId, reportedScore, rubric, "문항 피드백");
    }

    /** 원하는 0~25점 합계를 루브릭 배점 순서로 분배해 정상 범위의 원시 항목을 만든다. */
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
