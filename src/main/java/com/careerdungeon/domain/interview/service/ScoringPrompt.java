package com.careerdungeon.domain.interview.service;

/** 채점 프롬프트의 system/user 문자열을 LLM 경계로 전달하는 값 객체다. */
public record ScoringPrompt(
        String systemPrompt,
        String userPrompt
) {
}
