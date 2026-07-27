package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.progress.model.ProgressGaugeResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 최종 판정에 따른 게이지·순차 해금·뱃지 지급을 하나의 작업으로 조정한다. */
@Service
public class StageProgressionService {

    private static final int MAXIMUM_MVP_BADGE_STAGE = 3;

    private final ProgressGaugeService progressGaugeService;
    private final BadgeAwardService badgeAwardService;

    /** 게이지 정책과 뱃지 지급 서비스를 주입받는다. */
    public StageProgressionService(
            ProgressGaugeService progressGaugeService,
            BadgeAwardService badgeAwardService) {
        this.progressGaugeService = progressGaugeService;
        this.badgeAwardService = badgeAwardService;
    }

    /**
     * 최종 점수를 반영하고 실제로 다음 Stage가 열렸을 때 대응하는 뱃지를 함께 지급한다.
     * Stage4·Stage5는 MVP 면접 범위 밖이므로 지급하지 않는다.
     */
    @Transactional
    public ProgressGaugeResult applyFinalScore(long userId, int completedStage, int totalScore) {
        ProgressGaugeResult result = progressGaugeService.applyFinalScore(
                userId,
                completedStage,
                totalScore);

        if (result.changed() && result.unlockedLevel() <= MAXIMUM_MVP_BADGE_STAGE) {
            badgeAwardService.awardForStage(userId, result.unlockedLevel());
        }
        return result;
    }
}
