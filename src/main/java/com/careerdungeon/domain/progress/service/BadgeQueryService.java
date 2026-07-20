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

    /** 사용자 뱃지 저장소를 주입해 조회 서비스를 구성한다. */
    public BadgeQueryService(UserBadgeRepository userBadgeRepository) {
        this.userBadgeRepository = userBadgeRepository;
    }

    /** 인증 사용자의 획득 뱃지를 Stage 오름차순으로 반환한다. */
    public UserBadgeListResponse getMyBadges(long userId) {
        List<UserBadgeResponse> badges = userBadgeRepository
                .findAllWithBadgeByUserIdOrderByStage(userId)
                .stream()
                .map(UserBadgeResponse::from)
                .toList();
        return new UserBadgeListResponse(badges);
    }
}
