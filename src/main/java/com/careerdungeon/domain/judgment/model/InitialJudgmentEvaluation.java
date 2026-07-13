package com.careerdungeon.domain.judgment.model;

import java.util.List;
import java.util.Objects;

/**
 * 최초 세 문항의 서버 확정 채점 결과.
 *
 * @param evaluations 서버가 확정한 questionId 1~3의 점수
 * @param totalScore 서버가 재계산한 최초 총점(0~75)
 * @param weakestQuestionId 꼬리질문 생성에 전달할 최저점 문항
 * @param passed 최초 세 문항만으로는 80점에 도달할 수 없어 항상 false인 호환 필드
 */
public record InitialJudgmentEvaluation(
        List<QuestionScore> evaluations,
        int totalScore,
        int weakestQuestionId,
        boolean passed
) {
    /** 최초 평가 목록을 null로 생성하거나 외부에서 변경하지 못하도록 방어한다. */
    public InitialJudgmentEvaluation {
        Objects.requireNonNull(evaluations, "최초 평가 목록은 필수입니다.");
        evaluations = List.copyOf(evaluations);
    }
}
