package com.careerdungeon.global.llm.exception;

/**
 * LLM provider request configuration or authentication failed in a way retry cannot fix.
 */
public class LlmProviderConfigException extends RuntimeException {

    private final Integer statusCode;

    public LlmProviderConfigException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
