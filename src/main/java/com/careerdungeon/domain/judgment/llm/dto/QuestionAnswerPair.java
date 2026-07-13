package com.careerdungeon.domain.judgment.llm.dto;

/**
 * 질문 생성 시 만든 비노출 모범답변과 사용자 답변의 채점 입력 쌍.
 *
 * @param questionId 문항 식별자
 * @param questionText 사용자에게 노출된 질문 본문
 * @param userAnswer 사용자가 제출한 답변
 * @param expectedAnswer 질문 생성 시 함께 만든 비노출 모범답변
 */
public record QuestionAnswerPair(
        int questionId,
        String questionText,
        String userAnswer,
        String expectedAnswer
) {
}
