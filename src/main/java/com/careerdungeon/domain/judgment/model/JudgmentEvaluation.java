package com.careerdungeon.domain.judgment.model;

import java.util.List;

/**
 * 영속화 전 단계의 서버 확정 채점 결과.
 *
 * @param evaluations 서버가 확정한 문항별 점수 목록
 * @param totalScore 서버가 재계산한 총점(0~100)
 * @param weakestQuestionId 서버가 선택한 최저점 문항
 * @param passed 총점 80점 이상 여부
 * @param overallFeedback LLM 원시 응답에서 검증을 통과한 종합 피드백
 */
public record JudgmentEvaluation(
        List<QuestionScore> evaluations,
        int totalScore,
        int weakestQuestionId,
        boolean passed,
        String overallFeedback
) {
    /**
     * 확정 결과가 생성된 뒤 문항 목록이 외부에서 변경되지 않도록 방어적 복사한다.
     */
    public JudgmentEvaluation {
        evaluations = List.copyOf(evaluations);
    }
}
