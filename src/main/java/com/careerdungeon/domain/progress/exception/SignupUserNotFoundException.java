package com.careerdungeon.domain.progress.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 가입 완료 신호의 사용자 식별자가 실제 사용자와 대응하지 않을 때 발생한다. */
public class SignupUserNotFoundException extends BusinessException {

    /** 조회하지 못한 사용자 식별자를 포함해 가입 사용자 미존재 예외를 생성한다. */
    public SignupUserNotFoundException(long userId) {
        super(
                "SIGNUP_USER_NOT_FOUND",
                "가입 초기화 대상 사용자를 찾을 수 없습니다: " + userId,
                HttpStatus.NOT_FOUND);
    }
}
