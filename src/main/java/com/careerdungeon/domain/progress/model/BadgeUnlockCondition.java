package com.careerdungeon.domain.progress.model;

import com.careerdungeon.domain.progress.exception.InvalidBadgeStageException;

import java.util.Arrays;

/** 뱃지 Stage와 지급 조건의 고정 매핑을 관리한다. */
public enum BadgeUnlockCondition {

    SIGNUP(1),
    LEVEL1_UNLOCK(2),
    LEVEL2_UNLOCK(3),
    LEVEL3_UNLOCK(4);

    private final int stage;

    /** 지급 조건에 대응하는 고정 Stage를 설정한다. */
    BadgeUnlockCondition(int stage) {
        this.stage = stage;
    }

    /** Stage 번호에 대응하는 지급 조건을 반환한다. */
    public static BadgeUnlockCondition fromStage(int stage) {
        return Arrays.stream(values())
                .filter(condition -> condition.stage == stage)
                .findFirst()
                .orElseThrow(() -> new InvalidBadgeStageException(stage));
    }

    /** 지급 조건에 대응하는 뱃지 Stage를 반환한다. */
    public int stage() {
        return stage;
    }
}
