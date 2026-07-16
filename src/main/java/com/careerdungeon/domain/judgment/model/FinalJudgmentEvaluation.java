package com.careerdungeon.domain.judgment.model;

import java.util.List;
import java.util.Objects;

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
    /** 최종 평가 목록을 null로 생성하거나 외부에서 변경하지 못하도록 방어한다. */
    public FinalJudgmentEvaluation {
        Objects.requireNonNull(evaluations, "최종 평가 목록은 필수입니다.");
        evaluations = List.copyOf(evaluations);
    }
}
