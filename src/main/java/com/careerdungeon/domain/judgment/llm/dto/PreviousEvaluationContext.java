package com.careerdungeon.domain.judgment.llm.dto;

/**
 * turn 4 점수에는 영향을 주지 않고 종합 피드백 생성에만 사용하는 최초 평가 컨텍스트.
 *
 * @param questionId 최초 문항 식별자(1~3)
 * @param questionText 사용자에게 제시한 질문
 * @param userAnswer 사용자가 제출한 답변
 * @param score 서버에서 이미 확정한 점수(0~25)
 * @param feedback 최초 채점에서 확정한 피드백
 */
public record PreviousEvaluationContext(
        int questionId,
        String questionText,
        String userAnswer,
        int score,
        String feedback
) {
}
