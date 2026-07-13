package com.careerdungeon.domain.judgment.llm.dto;

/** 질문 생성 시 만든 비노출 모범답변과 사용자 답변의 채점 입력 쌍. */
public record QuestionAnswerPair(
        int questionId,
        String questionText,
        String userAnswer,
        String expectedAnswer
) {
}
