package com.careerdungeon.domain.progress.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 지급해야 할 Stage의 뱃지 기준 데이터가 없을 때 발생한다. */
public class BadgeNotFoundException extends BusinessException {

    /** 누락된 Stage를 포함해 뱃지 기준 데이터 미존재 예외를 생성한다. */
    public BadgeNotFoundException(int stage) {
        super(
                "BADGE_NOT_FOUND",
                "지급할 뱃지 기준 데이터를 찾을 수 없습니다: Stage " + stage,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
