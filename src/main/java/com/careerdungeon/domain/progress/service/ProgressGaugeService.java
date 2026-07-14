package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.exception.UserProgressNotFoundException;
import com.careerdungeon.domain.progress.model.ProgressGaugeResult;
import com.careerdungeon.domain.progress.model.StageGaugePolicy;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 최종 채점 점수를 스테이지 클리어 기반 누적 신뢰도 게이지로 반영한다. */
@Service
public class ProgressGaugeService {

    private static final int PASSING_SCORE = 80;
    private static final int MINIMUM_SCORE = 0;
    private static final int MAXIMUM_SCORE = 100;

    private final UserUnlockStatusRepository userUnlockStatusRepository;

    /** 상위 트랜잭션에서 사용할 사용자 진행도 저장소를 주입받는다. */
    public ProgressGaugeService(UserUnlockStatusRepository userUnlockStatusRepository) {
        this.userUnlockStatusRepository = userUnlockStatusRepository;
    }

    /**
     * 최종 총점을 서버 범위로 보정하고 80점 이상일 때만 해당 스테이지 클리어를 반영한다.
     * 게이지 갱신과 다음 스테이지 오픈은 같은 엔티티 변경으로 원자적으로 저장된다.
     * 뱃지 지급 서비스와 같은 상위 트랜잭션에서만 호출해 부분 반영을 방지한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ProgressGaugeResult applyFinalScore(long userId, int completedStage, int totalScore) {
        StageGaugePolicy policy = StageGaugePolicy.from(completedStage);
        UserUnlockStatus status = userUnlockStatusRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new UserProgressNotFoundException(userId));

        int clampedScore = clamp(totalScore, MINIMUM_SCORE, MAXIMUM_SCORE);
        if (clampedScore < PASSING_SCORE) {
            return ProgressGaugeResult.from(status, false);
        }

        boolean changed = status.completeStage(policy);
        return ProgressGaugeResult.from(status, changed);
    }

    /** 주어진 점수를 최종 판정 점수 범위인 0~100으로 제한한다. */
    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
