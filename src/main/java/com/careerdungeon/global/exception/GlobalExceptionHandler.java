package com.careerdungeon.global.exception;

import com.careerdungeon.global.common.ApiErrorResponse;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 도메인 전체 공통 예외 핸들러.
 *
 * <p>응답 포맷은 표지민의 공통 응답 계약(CM-002) 확정 후 {@link ApiErrorResponse}와 맞춰 갱신한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * LLM 재시도 2회 후 최종 실패 — 사용자에게 재시도 안내 (failure-policy.md §2).
     * 조용히 빈 값을 반환하지 않는다.
     */
    @ExceptionHandler(LlmPermanentFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleLlmPermanentFailure(LlmPermanentFailureException e) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of("LLM_FAILURE",
                        "질문 생성/채점에 실패했습니다. 잠시 후 다시 시도해 주세요."));
    }

    /**
     * LLM 응답 스키마 이탈 — 재시도 중 발생, 보통 {@link LlmPermanentFailureException}으로
     * 감싸지지만 재시도 설정 외부에서 직접 던져질 경우를 대비해 핸들링한다.
     */
    @ExceptionHandler(LlmSchemaValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleLlmSchemaValidation(LlmSchemaValidationException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(ApiErrorResponse.of("LLM_SCHEMA_ERROR",
                        "LLM 응답 형식이 올바르지 않습니다. 잠시 후 다시 시도해 주세요."));
    }
}
