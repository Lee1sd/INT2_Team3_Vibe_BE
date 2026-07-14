package com.careerdungeon.domain.progress.model;

import com.careerdungeon.domain.progress.exception.InvalidProgressStageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 스테이지별 증가량과 누적 게이지 정책을 검증한다. */
class StageGaugePolicyTest {

    /** 확정된 30/30/40 증가량과 30/60/100 누적값을 검증한다. */
    @Test
    @DisplayName("Stage별 신뢰도 게이지 정책은 30/30/40 증가와 30/60/100 누적값을 사용한다")
    void stage별_게이지_정책을_반환한다() {
        assertThat(StageGaugePolicy.STAGE_1.increaseAmount()).isEqualTo(30);
        assertThat(StageGaugePolicy.STAGE_1.cumulativeGauge()).isEqualTo(30);
        assertThat(StageGaugePolicy.STAGE_2.increaseAmount()).isEqualTo(30);
        assertThat(StageGaugePolicy.STAGE_2.cumulativeGauge()).isEqualTo(60);
        assertThat(StageGaugePolicy.STAGE_3.increaseAmount()).isEqualTo(40);
        assertThat(StageGaugePolicy.STAGE_3.cumulativeGauge()).isEqualTo(100);
    }

    /** MVP와 스트레치골 정책에 없는 스테이지 번호를 거부하는지 검증한다. */
    @Test
    @DisplayName("지원하지 않는 Stage는 진행도 예외를 발생시킨다")
    void 지원하지_않는_stage를_거부한다() {
        assertThatThrownBy(() -> StageGaugePolicy.from(4))
                .isInstanceOf(InvalidProgressStageException.class)
                .hasMessageContaining("4");
    }
}
