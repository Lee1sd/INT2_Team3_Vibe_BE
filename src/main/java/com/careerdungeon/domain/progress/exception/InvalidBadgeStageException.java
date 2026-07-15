package com.careerdungeon.domain.progress.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 지원 범위를 벗어난 뱃지 Stage가 입력될 때 발생한다. */
public class InvalidBadgeStageException extends BusinessException {

    /** 잘못된 Stage 번호를 포함해 뱃지 Stage 예외를 생성한다. */
    public InvalidBadgeStageException(int stage) {
        super(
                "INVALID_BADGE_STAGE",
                "지원하지 않는 뱃지 스테이지입니다: " + stage,
                HttpStatus.BAD_REQUEST);
    }
}
