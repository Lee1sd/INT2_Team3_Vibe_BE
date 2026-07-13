package com.careerdungeon.global.exception;

import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage(), e.getStatus().value()));
    }

    // fieldErrors[]는 api-contract.md 확정 기준으로 미사용 — 첫 번째 오류 메시지만 반환
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", message, 400));
    }

    // 재시도(최대 3회) 소진 후 전파된 경우 — NFR-05, failure-policy.md §2
    @ExceptionHandler(LlmSchemaValidationException.class)
    public ResponseEntity<ErrorResponse> handleLlmSchema(LlmSchemaValidationException e) {
        log.error("LLM 스키마 검증 실패 (재시도 소진): {}", e.getMessage());
        return ResponseEntity.status(503)
                .body(new ErrorResponse("LLM_UNAVAILABLE", "AI 응답 처리에 실패했습니다. 잠시 후 다시 시도해주세요.", 503));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("예상치 못한 예외 발생", e);
        return ResponseEntity.status(500)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다.", 500));
    }
}