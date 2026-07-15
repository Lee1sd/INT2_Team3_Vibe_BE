package com.careerdungeon.domain.interview.service;

public record QuestionGenerationPrompt(
        String systemPrompt,
        String userPrompt
) {
}
