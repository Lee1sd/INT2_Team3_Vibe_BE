package com.careerdungeon.domain.progress.controller;

import com.careerdungeon.domain.progress.dto.UserBadgeListResponse;
import com.careerdungeon.domain.progress.service.BadgeQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BG-001 사용자 획득 뱃지 조회 엔드포인트를 제공한다. */
@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeQueryService badgeQueryService;

    /** 뱃지 조회 서비스를 주입해 컨트롤러를 구성한다. */
    public BadgeController(BadgeQueryService badgeQueryService) {
        this.badgeQueryService = badgeQueryService;
    }

    /** 인증 사용자가 획득한 뱃지 목록을 반환한다. */
    @GetMapping("/me")
    public UserBadgeListResponse getMyBadges(@AuthenticationPrincipal Long userId) {
        return badgeQueryService.getMyBadges(userId);
    }
}
