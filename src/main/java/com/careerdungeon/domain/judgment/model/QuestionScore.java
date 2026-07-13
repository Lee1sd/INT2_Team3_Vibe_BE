package com.careerdungeon.domain.judgment.model;

/**
 * 서버에서 루브릭 항목을 clamp한 뒤 확정한 문항 점수 값.
 *
 * @param questionId 문항 식별자
 * @param score 확정 문항 점수(0~25)
 * @param feedback 검증을 통과한 문항별 피드백. 최종 응답의 기존 1~3번 문항은 null 가능
 */
public record QuestionScore(
        int questionId,
        int score,
        String feedback
) {
}
