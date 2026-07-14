package com.careerdungeon.domain.progress.entity;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.progress.exception.InvalidStageProgressionException;
import com.careerdungeon.domain.progress.model.StageGaugePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Check;

/** 사용자별 현재 열린 스테이지와 누적 신뢰도 게이지를 보관한다. */
@Entity
@Table(name = "user_unlock_status")
@Check(constraints = "unlocked_level between 1 and 4 and progress_gauge between 0 and 100")
public class UserUnlockStatus {

    private static final int INITIAL_UNLOCKED_LEVEL = 1;
    private static final int INITIAL_PROGRESS_GAUGE = 0;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_unlock_status_user"))
    private User user;

    @Column(name = "unlocked_level", nullable = false)
    private int unlockedLevel;

    @Column(name = "progress_gauge", nullable = false)
    private int progressGauge;

    /** JPA가 엔티티를 복원할 때 사용하는 기본 생성자다. */
    protected UserUnlockStatus() {
    }

    private UserUnlockStatus(User user, int unlockedLevel, int progressGauge) {
        this.user = user;
        this.unlockedLevel = unlockedLevel;
        this.progressGauge = progressGauge;
    }

    /** 가입 완료 사용자의 초기 상태인 Stage1 오픈·게이지 0%를 생성한다. */
    public static UserUnlockStatus initialFor(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("저장된 사용자 정보가 필요합니다.");
        }
        return new UserUnlockStatus(user, INITIAL_UNLOCKED_LEVEL, INITIAL_PROGRESS_GAUGE);
    }

    /**
     * 현재 열린 스테이지를 클리어하고 다음 스테이지와 누적 게이지를 함께 갱신한다.
     * 이미 반영한 이전 스테이지 요청은 멱등하게 무시한다.
     *
     * @return 실제 상태가 변경됐으면 true, 이미 반영된 요청이면 false
     */
    public boolean completeStage(StageGaugePolicy policy) {
        int completedStage = policy.completedStage();
        if (completedStage < unlockedLevel) {
            return false;
        }
        if (completedStage > unlockedLevel) {
            throw new InvalidStageProgressionException(unlockedLevel, completedStage);
        }

        // 누적 목표값을 직접 대입해 재시도와 기존 데이터 편차에도 30/60/100 불변식을 유지한다.
        unlockedLevel = policy.nextUnlockedLevel();
        progressGauge = Math.min(100, policy.cumulativeGauge());
        return true;
    }

    /** 사용자 식별자를 반환한다. */
    public Long getUserId() {
        return userId;
    }

    /** 현재 열려 있는 가장 높은 스테이지를 반환한다. */
    public int getUnlockedLevel() {
        return unlockedLevel;
    }

    /** 클리어한 스테이지 기준 누적 신뢰도 게이지를 반환한다. */
    public int getProgressGauge() {
        return progressGauge;
    }
}
