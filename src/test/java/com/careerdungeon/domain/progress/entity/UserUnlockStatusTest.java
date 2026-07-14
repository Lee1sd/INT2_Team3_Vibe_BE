package com.careerdungeon.domain.progress.entity;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.progress.exception.InvalidStageProgressionException;
import com.careerdungeon.domain.progress.model.StageGaugePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 사용자 진행도 엔티티의 초기값과 순차 클리어 불변식을 검증한다. */
class UserUnlockStatusTest {

    /** 가입 직후에는 Stage1만 열리고 클리어 게이지는 0%인지 검증한다. */
    @Test
    @DisplayName("가입 직후 Stage1은 열려 있고 신뢰도 게이지는 0%다")
    void 초기_진행도_상태를_생성한다() {
        UserUnlockStatus status = initialStatus();

        assertThat(status.getUnlockedLevel()).isEqualTo(1);
        assertThat(status.getProgressGauge()).isZero();
    }

    /** Stage1부터 Stage3까지 순서대로 클리어했을 때 누적 게이지를 검증한다. */
    @Test
    @DisplayName("Stage1부터 Stage3까지 순서대로 클리어하면 게이지가 30%, 60%, 100%가 된다")
    void stage를_순서대로_클리어한다() {
        UserUnlockStatus status = initialStatus();

        assertThat(status.completeStage(StageGaugePolicy.STAGE_1)).isTrue();
        assertThat(status.getUnlockedLevel()).isEqualTo(2);
        assertThat(status.getProgressGauge()).isEqualTo(30);

        assertThat(status.completeStage(StageGaugePolicy.STAGE_2)).isTrue();
        assertThat(status.getUnlockedLevel()).isEqualTo(3);
        assertThat(status.getProgressGauge()).isEqualTo(60);

        assertThat(status.completeStage(StageGaugePolicy.STAGE_3)).isTrue();
        assertThat(status.getUnlockedLevel()).isEqualTo(4);
        assertThat(status.getProgressGauge()).isEqualTo(100);
    }

    /** 이미 반영한 Stage의 재처리가 게이지를 중복 증가시키지 않는지 검증한다. */
    @Test
    @DisplayName("이미 클리어한 Stage를 다시 처리하면 상태를 변경하지 않는다")
    void 이미_반영한_stage는_멱등하게_무시한다() {
        UserUnlockStatus status = initialStatus();
        status.completeStage(StageGaugePolicy.STAGE_1);

        boolean changed = status.completeStage(StageGaugePolicy.STAGE_1);

        assertThat(changed).isFalse();
        assertThat(status.getUnlockedLevel()).isEqualTo(2);
        assertThat(status.getProgressGauge()).isEqualTo(30);
    }

    /** 현재 열린 Stage보다 높은 Stage를 먼저 클리어할 수 없는지 검증한다. */
    @Test
    @DisplayName("현재 Stage를 건너뛴 클리어 요청은 거부하고 상태를 유지한다")
    void stage_건너뛰기를_거부한다() {
        UserUnlockStatus status = initialStatus();

        assertThatThrownBy(() -> status.completeStage(StageGaugePolicy.STAGE_2))
                .isInstanceOf(InvalidStageProgressionException.class);
        assertThat(status.getUnlockedLevel()).isEqualTo(1);
        assertThat(status.getProgressGauge()).isZero();
    }

    /** 영속화된 사용자 조건을 만족하는 단위 테스트용 초기 진행도를 생성한다. */
    private UserUnlockStatus initialStatus() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        return UserUnlockStatus.initialFor(user);
    }
}
