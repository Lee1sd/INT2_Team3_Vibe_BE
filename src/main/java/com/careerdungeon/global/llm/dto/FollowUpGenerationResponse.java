package com.careerdungeon.global.llm.dto;

/**
 * 최저점 문항을 보완하기 위한 꼬리질문 생성 응답.
 *
 * @param followUpQuestion 사용자에게 제시할 꼬리질문 본문
 * @param expectedAnswer   최종 채점에 사용할 모범답안
 */
public record FollowUpGenerationResponse(
        String followUpQuestion,
        String expectedAnswer
) {}
