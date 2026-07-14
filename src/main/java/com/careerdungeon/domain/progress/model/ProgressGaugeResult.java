package com.careerdungeon.domain.progress.model;

import com.careerdungeon.domain.progress.entity.UserUnlockStatus;

/** 신뢰도 게이지 반영 후 클라이언트 연동에 필요한 진행도 상태다. */
public record ProgressGaugeResult(
        int unlockedLevel,
        int progressGauge,
        boolean changed
) {
    /** 엔티티의 현재 상태와 변경 여부를 불변 응답 값으로 변환한다. */
    public static ProgressGaugeResult from(UserUnlockStatus status, boolean changed) {
        return new ProgressGaugeResult(
                status.getUnlockedLevel(),
                status.getProgressGauge(),
                changed);
    }
}
