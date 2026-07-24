package com.careerdungeon.global.llm.dto;

/**
 * 최종 종합 피드백 생성에만 사용하는 최초 평가 읽기 전용 컨텍스트.
 *
 * @param turn 최초 질문 순서(1~4)
 * @param questionText 사용자에게 제시한 질문
 * @param userAnswer 사용자가 제출한 답변
 * @param score 서버에서 이미 확정한 점수(0~25)
 * @param feedback 최초 채점에서 확정한 피드백
 */
public record PreviousEvaluationContext(
        int turn,
        String questionText,
        String userAnswer,
        int score,
        String feedback
) {
}
