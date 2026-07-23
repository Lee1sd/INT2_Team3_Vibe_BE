package com.careerdungeon.domain.progress.dto;

import java.util.List;

/** BG-001의 기존 보유 목록과 잠금 상태를 포함한 전체 도감 배열 계약을 표현한다. */
public record UserBadgeListResponse(
        List<UserBadgeResponse> badges,
        List<UserBadgeResponse> catalog
) {

    /** 외부에서 두 응답 목록을 변경하지 못하도록 불변 복사본을 보관한다. */
    public UserBadgeListResponse {
        badges = List.copyOf(badges);
        catalog = List.copyOf(catalog);
    }
}
