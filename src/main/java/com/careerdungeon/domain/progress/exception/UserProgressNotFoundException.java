package com.careerdungeon.domain.progress.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 가입 시 생성돼야 할 사용자 진행도 상태가 존재하지 않을 때 발생한다. */
public class UserProgressNotFoundException extends BusinessException {

    /** 조회하지 못한 사용자 식별자를 포함해 진행도 미존재 예외를 생성한다. */
    public UserProgressNotFoundException(long userId) {
        super(
                "PROGRESS_NOT_FOUND",
                "사용자 진행도 정보를 찾을 수 없습니다: " + userId,
                HttpStatus.NOT_FOUND);
    }
}
