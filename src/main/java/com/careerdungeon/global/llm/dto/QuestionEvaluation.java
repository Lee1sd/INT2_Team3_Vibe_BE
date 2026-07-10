package com.careerdungeon.global.llm.dto;

/**
 * 문항 하나에 대한 LLM 평가 결과.
 *
 * @param turn     질문 순서 (1~4)
 * @param score    LLM이 반환한 원시 점수 (0~25 의도, 범위 이탈 가능) — clamp는 ③의 책임
 * @param feedback 사용자에게 노출되는 피드백 문자열
 */
public record QuestionEvaluation(
        int turn,
        int score,
        String feedback
) {}
