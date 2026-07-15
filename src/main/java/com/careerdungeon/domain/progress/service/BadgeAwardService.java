package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.entity.UserBadge;
import com.careerdungeon.domain.progress.exception.BadgeNotFoundException;
import com.careerdungeon.domain.progress.model.BadgeUnlockCondition;
import com.careerdungeon.domain.progress.repository.BadgeRepository;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 확정된 Stage 매핑에 따라 사용자 뱃지를 멱등하게 지급한다. */
@Service
public class BadgeAwardService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;

    /** 뱃지 기준 데이터와 사용자 획득 기록 저장소를 주입받는다. */
    public BadgeAwardService(
            BadgeRepository badgeRepository,
            UserBadgeRepository userBadgeRepository) {
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
    }

    /**
     * 지정 Stage의 뱃지를 조회해 아직 보유하지 않은 사용자에게 한 번만 지급한다.
     * 진행도 변경과 같은 상위 트랜잭션에서만 호출해 부분 지급을 방지한다.
     *
     * @return 새 획득 레코드를 만들었으면 true, 이미 보유 중이면 false
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean awardForStage(long userId, int stage) {
        BadgeUnlockCondition.fromStage(stage);
        Badge badge = badgeRepository.findByStage(stage)
                .orElseThrow(() -> new BadgeNotFoundException(stage));

        if (userBadgeRepository.existsByUserIdAndBadgeId(userId, badge.getId())) {
            return false;
        }

        userBadgeRepository.save(UserBadge.award(userId, badge));
        return true;
    }
}
