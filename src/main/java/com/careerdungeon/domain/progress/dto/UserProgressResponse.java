package com.careerdungeon.domain.progress.dto;

import com.careerdungeon.domain.progress.entity.UserUnlockStatus;

/** UM-001에서 사용자 해금 레벨과 누적 진행도를 반환한다. */
public record UserProgressResponse(
        int unlockedLevel,
        int progressGauge
) {

    /** 저장된 진행도 상태를 API 응답으로 변환한다. */
    public static UserProgressResponse from(UserUnlockStatus status) {
        return new UserProgressResponse(status.getUnlockedLevel(), status.getProgressGauge());
    }
}
