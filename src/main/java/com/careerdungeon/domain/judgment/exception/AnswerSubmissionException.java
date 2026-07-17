package com.careerdungeon.domain.judgment.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 답변 제출 상태·소유권·문항 구성 계약 위반을 공통 API 예외 형식으로 전달한다. */
public class AnswerSubmissionException extends BusinessException {

    /** 공통 에러 코드·사용자 메시지·HTTP 상태로 답변 제출 예외를 생성한다. */
    public AnswerSubmissionException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
