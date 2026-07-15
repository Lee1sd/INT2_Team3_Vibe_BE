package com.careerdungeon.domain.progress.model;

import com.careerdungeon.domain.progress.exception.InvalidBadgeStageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 뱃지 Stage와 지급 조건 매핑의 경계를 검증한다. */
class BadgeUnlockConditionTest {

    /** 가입부터 Lv.3 해금까지 네 Stage가 확정 조건에 대응하는지 검증한다. */
    @Test
    @DisplayName("Stage1~4는 가입/Lv.1/Lv.2/Lv.3 해금 조건에 순서대로 대응한다")
    void stage와_지급_조건을_매핑한다() {
        assertThat(BadgeUnlockCondition.fromStage(1)).isEqualTo(BadgeUnlockCondition.SIGNUP);
        assertThat(BadgeUnlockCondition.fromStage(2)).isEqualTo(BadgeUnlockCondition.LEVEL1_UNLOCK);
        assertThat(BadgeUnlockCondition.fromStage(3)).isEqualTo(BadgeUnlockCondition.LEVEL2_UNLOCK);
        assertThat(BadgeUnlockCondition.fromStage(4)).isEqualTo(BadgeUnlockCondition.LEVEL3_UNLOCK);
    }

    /** 1~4 밖의 Stage가 도메인 예외로 거부되는지 검증한다. */
    @Test
    @DisplayName("지원 범위 밖의 뱃지 Stage는 거부한다")
    void 잘못된_stage를_거부한다() {
        assertThatThrownBy(() -> BadgeUnlockCondition.fromStage(0))
                .isInstanceOf(InvalidBadgeStageException.class);
        assertThatThrownBy(() -> BadgeUnlockCondition.fromStage(5))
                .isInstanceOf(InvalidBadgeStageException.class);
    }
}
