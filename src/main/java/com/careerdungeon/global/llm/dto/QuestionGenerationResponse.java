package com.careerdungeon.global.llm.dto;

import java.util.List;

/**
 * LLM이 반환하는 질문 생성 결과. questions 크기는 정상 응답 시 3 (FR-03).
 */
public record QuestionGenerationResponse(
        List<GeneratedQuestion> questions
) {}
