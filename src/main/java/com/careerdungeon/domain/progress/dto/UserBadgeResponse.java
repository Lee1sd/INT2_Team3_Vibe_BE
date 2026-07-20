package com.careerdungeon.domain.progress.dto;

import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.entity.UserBadge;

import java.time.Instant;

/** BG-001에서 사용자에게 표시할 획득 뱃지 한 건을 반환한다. */
public record UserBadgeResponse(
        long badgeId,
        int stage,
        String name,
        String imageUrl,
        Instant acquiredAt
) {

    /** 획득 기록과 뱃지 기준 데이터를 API 응답으로 변환한다. */
    public static UserBadgeResponse from(UserBadge userBadge) {
        Badge badge = userBadge.getBadge();
        return new UserBadgeResponse(
                badge.getId(),
                badge.getStage(),
                badge.getName(),
                badge.getImageUrl(),
                userBadge.getAcquiredAt());
    }
}
