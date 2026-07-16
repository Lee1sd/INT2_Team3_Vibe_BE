package com.careerdungeon.global.llm.exception;

/**
 * LLM 응답이 기대하는 JSON 스키마를 벗어날 때 던진다.
 * 호출 지점에서 {@code @Retryable(maxAttempts = 2)}로 최대 1회 재요청 후
 * 실패 처리한다 (NFR-05, failure-policy.md §2).
 */
public class LlmSchemaValidationException extends RuntimeException {

    private final Integer statusCode;

    public LlmSchemaValidationException(String message) {
        super(message);
        this.statusCode = null;
    }

    public LlmSchemaValidationException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = null;
    }

    public LlmSchemaValidationException(String message, Throwable cause, Integer statusCode) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
