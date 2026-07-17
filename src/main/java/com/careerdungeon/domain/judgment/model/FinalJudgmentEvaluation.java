package com.careerdungeon.domain.judgment.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 최초 확정 세 문항과 신규 꼬리질문 점수를 합친 서버 확정 최종 채점 결과.
 *
 * @param evaluations 서버가 확정한 questionId 1~4의 점수
 * @param totalScore 서버가 재계산한 최종 총점(0~100)
 * @param passed 최종 총점 80점 이상 여부
 * @param overallFeedback 검증을 통과한 꼬리질문 반영 종합 피드백
 */
public record FinalJudgmentEvaluation(
        List<QuestionScore> evaluations,
        int totalScore,
        boolean passed,
        String overallFeedback
) {
    private static final int PASSING_SCORE = 80;
    private static final Set<Integer> REQUIRED_QUESTION_IDS = Set.of(1, 2, 3, 4);

    /** 최종 평가 목록·총점·합격 여부·피드백 불변식을 생성 시점에 방어한다. */
    public FinalJudgmentEvaluation {
        Objects.requireNonNull(evaluations, "최종 평가 목록은 필수입니다.");
        if (totalScore < 0 || totalScore > 100) {
            throw new IllegalArgumentException("최종 총점은 0~100이어야 합니다.");
        }
        if (passed != (totalScore >= PASSING_SCORE)) {
            throw new IllegalArgumentException("최종 합격 여부는 총점 80점 기준과 일치해야 합니다.");
        }
        if (overallFeedback == null || overallFeedback.isBlank()) {
            throw new IllegalArgumentException("최종 종합 피드백은 필수입니다.");
        }
        if (evaluations.size() != REQUIRED_QUESTION_IDS.size()
                || evaluations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("최종 평가 문항 구성은 1,2,3,4여야 합니다.");
        }
        Set<Integer> questionIds = evaluations.stream()
                .map(QuestionScore::questionId)
                .collect(Collectors.toSet());
        boolean invalidScore = evaluations.stream().anyMatch(evaluation ->
                evaluation.score() < 0 || evaluation.score() > 25);
        int calculatedTotalScore = evaluations.stream().mapToInt(QuestionScore::score).sum();
        if (!questionIds.equals(REQUIRED_QUESTION_IDS)
                || invalidScore
                || calculatedTotalScore != totalScore) {
            throw new IllegalArgumentException("최종 평가 문항과 총점이 일치하지 않습니다.");
        }
        evaluations = List.copyOf(evaluations);
    }
}
