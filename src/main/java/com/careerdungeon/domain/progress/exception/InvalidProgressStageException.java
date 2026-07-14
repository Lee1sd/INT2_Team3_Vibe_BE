package com.careerdungeon.domain.progress.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 신뢰도 게이지 정책이 지원하지 않는 스테이지가 전달됐을 때 발생한다. */
public class InvalidProgressStageException extends BusinessException {

    /** 잘못된 스테이지 번호를 공통 예외 응답 계약으로 변환한다. */
    public InvalidProgressStageException(int completedStage) {
        super(
                "PROGRESS_STAGE_INVALID",
                "신뢰도 게이지를 반영할 수 없는 스테이지입니다: " + completedStage,
                HttpStatus.BAD_REQUEST);
    }
}
