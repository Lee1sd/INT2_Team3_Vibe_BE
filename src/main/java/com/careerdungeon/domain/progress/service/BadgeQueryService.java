package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.progress.dto.UserBadgeListResponse;
import com.careerdungeon.domain.progress.dto.UserBadgeResponse;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 인증 사용자가 획득한 뱃지를 표시 순서에 맞춰 조회한다. */
@Service
@Transactional(readOnly = true)
public class BadgeQueryService {

    private final UserBadgeRepository userBadgeRepository;
    private final BadgeImageUrlService badgeImageUrlService;

    /** 사용자 뱃지 저장소와 환경별 이미지 URL 생성기를 주입해 조회 서비스를 구성한다. */
    public BadgeQueryService(
            UserBadgeRepository userBadgeRepository,
            BadgeImageUrlService badgeImageUrlService) {
        this.userBadgeRepository = userBadgeRepository;
        this.badgeImageUrlService = badgeImageUrlService;
    }

    /** 인증 사용자의 획득 뱃지를 Stage 오름차순으로 반환한다. */
    public UserBadgeListResponse getMyBadges(long userId) {
        List<UserBadgeResponse> badges = userBadgeRepository
                .findAllWithBadgeByUserIdOrderByStage(userId)
                .stream()
                .map(userBadge -> UserBadgeResponse.from(
                        userBadge,
                        badgeImageUrlService.createImageUrl(
                                userBadge.getBadge().getStage(),
                                userBadge.getBadge().getImageKey())))
                .toList();
        return new UserBadgeListResponse(badges);
    }
}
