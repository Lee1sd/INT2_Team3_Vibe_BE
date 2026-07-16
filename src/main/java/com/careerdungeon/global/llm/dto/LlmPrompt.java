package com.careerdungeon.global.llm.dto;

public record LlmPrompt(
        String systemPrompt,
        String userPrompt
) {
    public LlmPrompt {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("systemPrompt must not be blank");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("userPrompt must not be blank");
        }
        systemPrompt = systemPrompt.strip();
        userPrompt = userPrompt.strip();
    }
}
