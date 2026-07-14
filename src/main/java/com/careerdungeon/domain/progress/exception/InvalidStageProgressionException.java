package com.careerdungeon.domain.progress.exception;

import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** 현재 열린 스테이지를 건너뛴 클리어 요청이 들어왔을 때 발생한다. */
public class InvalidStageProgressionException extends BusinessException {

    /** 현재 열린 스테이지와 요청 스테이지를 포함한 충돌 예외를 생성한다. */
    public InvalidStageProgressionException(int unlockedLevel, int completedStage) {
        super(
                "PROGRESS_STAGE_SEQUENCE_VIOLATION",
                "현재 열린 스테이지 " + unlockedLevel
                        + "을 먼저 클리어해야 합니다. 요청 스테이지: " + completedStage,
                HttpStatus.CONFLICT);
    }
}
