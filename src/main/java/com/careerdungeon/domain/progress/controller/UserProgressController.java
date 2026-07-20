package com.careerdungeon.domain.progress.controller;

import com.careerdungeon.domain.progress.dto.UserProgressResponse;
import com.careerdungeon.domain.progress.service.ProgressQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** UM-001 사용자 진행도 조회 엔드포인트를 제공한다. */
@RestController
@RequestMapping("/api/users/me")
public class UserProgressController {

    private final ProgressQueryService progressQueryService;

    /** 진행도 조회 서비스를 주입해 컨트롤러를 구성한다. */
    public UserProgressController(ProgressQueryService progressQueryService) {
        this.progressQueryService = progressQueryService;
    }

    /** 인증 사용자의 현재 해금 레벨과 누적 게이지를 반환한다. */
    @GetMapping("/progress")
    public UserProgressResponse getMyProgress(@AuthenticationPrincipal Long userId) {
        return progressQueryService.getMyProgress(userId);
    }
}
