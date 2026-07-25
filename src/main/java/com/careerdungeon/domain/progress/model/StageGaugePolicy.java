package com.careerdungeon.domain.progress.model;

import com.careerdungeon.domain.progress.exception.InvalidProgressStageException;

import java.util.Arrays;

/** 스테이지별 통과 점수와 신뢰도 게이지 증가량·누적 목표값을 한곳에서 관리한다. */
public enum StageGaugePolicy {

    STAGE_1(1, 60, 30, 30),
    STAGE_2(2, 80, 30, 60),
    // Lv.3 면접은 MVP 범위 밖이므로 기존 80점 기준을 유지해 스트레치골 동작만 보존한다.
    STAGE_3(3, 80, 40, 100);

    private final int completedStage;
    private final int passingScore;
    private final int increaseAmount;
    private final int cumulativeGauge;

    StageGaugePolicy(
            int completedStage,
            int passingScore,
            int increaseAmount,
            int cumulativeGauge) {
        this.completedStage = completedStage;
        this.passingScore = passingScore;
        this.increaseAmount = increaseAmount;
        this.cumulativeGauge = cumulativeGauge;
    }

    /** 입력받은 클리어 스테이지에 해당하는 정책을 반환한다. */
    public static StageGaugePolicy from(int completedStage) {
        return Arrays.stream(values())
                .filter(policy -> policy.completedStage == completedStage)
                .findFirst()
                .orElseThrow(() -> new InvalidProgressStageException(completedStage));
    }

    /** 클리어한 스테이지 번호를 반환한다. */
    public int completedStage() {
        return completedStage;
    }

    /** 해당 스테이지를 통과하기 위한 최종 총점 하한을 반환한다. */
    public int passingScore() {
        return passingScore;
    }

    /** 해당 스테이지를 클리어할 때 증가하는 게이지 양을 반환한다. */
    public int increaseAmount() {
        return increaseAmount;
    }

    /** 해당 스테이지까지 클리어했을 때의 누적 게이지를 반환한다. */
    public int cumulativeGauge() {
        return cumulativeGauge;
    }

    /** 현재 스테이지 클리어 후 새로 열리는 다음 스테이지 번호를 반환한다. */
    public int nextUnlockedLevel() {
        return completedStage + 1;
    }
}
