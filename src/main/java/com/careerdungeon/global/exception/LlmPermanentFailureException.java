package com.careerdungeon.global.exception;

import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;

/**
 * LLM 재요청 3회(원래 1회 + 재시도 2회) 모두 실패 시 던진다 (NFR-05, failure-policy.md §2).
 * {@link com.careerdungeon.global.exception.GlobalExceptionHandler}가 잡아 사용자 안내 메시지를 반환한다.
 */
public class LlmPermanentFailureException extends RuntimeException {

    public LlmPermanentFailureException(String message, LlmSchemaValidationException cause) {
        super(message, cause);
    }
}
