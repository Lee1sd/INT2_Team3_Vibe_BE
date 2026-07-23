package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.progress.dto.UserBadgeListResponse;
import com.careerdungeon.domain.progress.dto.UserBadgeResponse;
import com.careerdungeon.domain.progress.entity.UserBadge;
import com.careerdungeon.domain.progress.repository.BadgeRepository;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 인증 사용자의 획득 상태를 포함한 전체 뱃지 도감을 표시 순서에 맞춰 조회한다. */
@Service
@Transactional(readOnly = true)
public class BadgeQueryService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BadgeImageUrlService badgeImageUrlService;

    /** 뱃지 기준·획득 저장소와 환경별 이미지 URL 생성기를 주입해 조회 서비스를 구성한다. */
    public BadgeQueryService(
            BadgeRepository badgeRepository,
            UserBadgeRepository userBadgeRepository,
            BadgeImageUrlService badgeImageUrlService) {
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.badgeImageUrlService = badgeImageUrlService;
    }

    /** 인증 사용자의 획득 여부와 관계없이 Stage1~4 뱃지 도감을 오름차순으로 반환한다. */
    public UserBadgeListResponse getMyBadges(long userId) {
        Map<Long, UserBadge> acquiredBadgesByBadgeId = userBadgeRepository
                .findAllWithBadgeByUserIdOrderByStage(userId)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        userBadge -> userBadge.getBadge().getId(),
                        Function.identity()));

        // 두 번의 고정 쿼리로 전체 기준 데이터와 사용자 획득 기록을 결합해 N+1 조회를 피한다.
        List<UserBadgeResponse> badges = badgeRepository.findAllByOrderByStageAsc()
                .stream()
                .map(badge -> UserBadgeResponse.from(
                        badge,
                        acquiredBadgesByBadgeId.get(badge.getId()),
                        badgeImageUrlService.createImageUrl(
                                badge.getStage(),
                                badge.getImageKey())))
                .toList();
        return new UserBadgeListResponse(badges);
    }
}
