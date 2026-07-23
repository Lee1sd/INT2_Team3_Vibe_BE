package com.careerdungeon.domain.progress.dto;

import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.entity.UserBadge;

import java.time.Instant;

/** BG-001에서 사용자에게 표시할 뱃지 도감 한 건과 획득 상태를 반환한다. */
public record UserBadgeResponse(
        long badgeId,
        int stage,
        String name,
        String imageUrl,
        boolean acquired,
        Instant acquiredAt
) {

    /** 뱃지 기준 데이터와 선택적인 획득 기록을 이미지 URL이 포함된 API 응답으로 변환한다. */
    public static UserBadgeResponse from(Badge badge, UserBadge userBadge, String imageUrl) {
        return new UserBadgeResponse(
                badge.getId(),
                badge.getStage(),
                badge.getName(),
                imageUrl,
                userBadge != null,
                userBadge == null ? null : userBadge.getAcquiredAt());
    }
}
