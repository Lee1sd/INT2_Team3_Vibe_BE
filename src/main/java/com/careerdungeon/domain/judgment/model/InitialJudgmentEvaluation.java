package com.careerdungeon.domain.judgment.model;

import java.util.List;
import java.util.Objects;

/**
 * 최초 네 문항의 서버 확정 채점 결과.
 *
 * @param evaluations 서버가 확정한 questionId 1~4의 점수
 * @param totalScore 서버가 재계산한 최초 총점(0~80)
 * @param weakestQuestionId 꼬리질문 생성에 전달할 최저점 문항
 * @param passed 꼬리질문 전에는 최종 판정하지 않으므로 항상 false인 호환 필드
 * @param passingScore 최종 판정에 적용할 세션 레벨별 통과 점수
 */
public record InitialJudgmentEvaluation(
        List<QuestionScore> evaluations,
        int totalScore,
        int weakestQuestionId,
        boolean passed,
        int passingScore
) {
    private static final int LEGACY_PASSING_SCORE = 80;

    /** 기존 호출 계약은 Lv.2 기준 80점을 기본값으로 유지한다. */
    public InitialJudgmentEvaluation(
            List<QuestionScore> evaluations,
            int totalScore,
            int weakestQuestionId,
            boolean passed) {
        this(evaluations, totalScore, weakestQuestionId, passed, LEGACY_PASSING_SCORE);
    }

    /** 최초 평가 목록을 null로 생성하거나 외부에서 변경하지 못하도록 방어한다. */
    public InitialJudgmentEvaluation {
        Objects.requireNonNull(evaluations, "최초 평가 목록은 필수입니다.");
        if (passingScore < 1 || passingScore > 100) {
            throw new IllegalArgumentException("통과 점수는 1~100이어야 합니다.");
        }
        evaluations = List.copyOf(evaluations);
    }
}
