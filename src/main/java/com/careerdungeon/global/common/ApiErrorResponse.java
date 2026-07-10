package com.careerdungeon.global.common;

/**
 * 공통 에러 응답 포맷 — CM-002 확정(2026-07-10, open-questions.md #6).
 * 표지민이 {@code global/exception/ErrorResponse}로 최종 구현 시 이 클래스와 통합할 것.
 *
 * @param status  HTTP 상태 코드 (예: 503, 502)
 * @param code    에러 코드 (예: "LLM_FAILURE", "VALIDATION_ERROR")
 * @param message 사용자에게 노출되는 안내 메시지
 */
public record ApiErrorResponse(
        int status,
        String code,
        String message
) {
    public static ApiErrorResponse of(int status, String code, String message) {
        return new ApiErrorResponse(status, code, message);
    }
}
