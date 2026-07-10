package com.careerdungeon.global.llm.dto;

/**
 * 채점 요청 시 질문-답변 쌍 하나를 나타낸다.
 *
 * @param turn           질문 순서 (1~4)
 * @param questionText   면접 질문 본문
 * @param userAnswer     사용자 답변
 * @param expectedAnswer 질문 생성 시 함께 만들어진 모범답변
 */
public record QuestionAnswerPair(
        int turn,
        String questionText,
        String userAnswer,
        String expectedAnswer
) {}
