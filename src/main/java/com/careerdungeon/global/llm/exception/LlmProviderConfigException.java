package com.careerdungeon.global.llm.exception;

/** 재시도로 복구할 수 없는 LLM 공급자 인증·요청 설정 오류를 나타낸다. */
public class LlmProviderConfigException extends RuntimeException {

    private final Integer statusCode;

    /** 공급자 오류 메시지와 HTTP 상태 코드를 보존한다. */
    public LlmProviderConfigException(String message, Integer statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /** 외부 응답 본문을 노출하지 않고 진단에 필요한 HTTP 상태 코드만 반환한다. */
    public Integer statusCode() {
        return statusCode;
    }
}
