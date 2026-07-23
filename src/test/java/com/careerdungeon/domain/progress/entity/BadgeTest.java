package com.careerdungeon.domain.progress.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Badge 생성 시 Stage와 고정 이미지 key의 도메인 불변식을 검증한다. */
class BadgeTest {

    /** Stage와 일치하는 고정 object key만 엔티티에 저장되는지 검증한다. */
    @Test
    @DisplayName("Stage에 대응하는 고정 뱃지 이미지 키로 생성한다")
    void createAcceptsExpectedImageKeyForStage() {
        Badge badge = Badge.create(2, "프로그래머쓱 LEVEL 2", "badges/Level2.png");

        assertThat(badge.getImageKey()).isEqualTo("badges/Level2.png");
    }

    /** 다른 Stage의 유효한 key도 현재 Stage에는 사용할 수 없는지 검증한다. */
    @Test
    @DisplayName("Stage와 다른 뱃지 이미지 키로 생성할 수 없다")
    void createRejectsMismatchedStageAndImageKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Badge.create(
                        1,
                        "프로그래머쓱 LEVEL 1",
                        "badges/Level4.png"));
    }
}
