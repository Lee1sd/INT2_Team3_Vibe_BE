package com.careerdungeon.global.common;

/**
 * 공통 에러 응답 포맷 (임시 — 표지민의 공통 응답 계약 확정 후 CM-002 기준으로 맞출 것).
 *
 * @param code    에러 코드 (예: "LLM_FAILURE", "VALIDATION_ERROR")
 * @param message 사용자에게 노출되는 안내 메시지
 */
public record ApiErrorResponse(
        String code,
        String message
) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message);
    }
}
