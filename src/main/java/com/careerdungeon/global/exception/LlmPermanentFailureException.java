package com.careerdungeon.global.exception;

import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;

/**
 * LLM 호출 2회(원래 1회 + 재시도 1회) 모두 실패 시 던진다 (NFR-05, failure-policy.md §2).
 * {@link com.careerdungeon.global.exception.GlobalExceptionHandler}가 잡아 사용자 안내 메시지를 반환한다.
 */
public class LlmPermanentFailureException extends RuntimeException {

    public LlmPermanentFailureException(String message, LlmSchemaValidationException cause) {
        super(message, cause);
    }

    /**
     * 재시도로 복구되지 않는 내부 입력(요청 계약 위반) 오류에 사용한다 — LLM 응답 스키마
     * 실패가 아니므로 cause가 없고, {@code @Retryable(retryFor = LlmSchemaValidationException.class)}
     * 대상이 아니어서 즉시 전파된다(코드래빗 지적 — 요청 검증 실패를 재시도하는 것은 낭비).
     */
    public LlmPermanentFailureException(String message) {
        super(message);
    }
}
