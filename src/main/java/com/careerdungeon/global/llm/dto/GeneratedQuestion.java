package com.careerdungeon.global.llm.dto;

/**
 * @param turn           질문 순서 (1~3, 꼬리질문 포함 시 4)
 * @param questionText   면접 질문 본문
 * @param expectedAnswer 모범답변 — 채점(FR-04) 시 비교 기준으로 사용, 사용자에게 미노출
 */
public record GeneratedQuestion(
        int turn,
        String questionText,
        String expectedAnswer
) {}
