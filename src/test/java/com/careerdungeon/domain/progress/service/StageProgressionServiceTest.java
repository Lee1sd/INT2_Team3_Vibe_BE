package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.model.ProgressGaugeResult;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** 최종 판정에 따른 순차 해금·게이지·뱃지 지급의 통합 동작을 검증한다. */
@DataJpaTest
@Import({StageProgressionService.class, ProgressGaugeService.class, BadgeAwardService.class})
class StageProgressionServiceTest {

    @Autowired
    StageProgressionService stageProgressionService;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    UserBadgeRepository userBadgeRepository;

    @Autowired
    UserRepository userRepository;

    long userId;

    /** 가입 직후 진행도를 준비한다. Stage2·3 기준 데이터는 Flyway seed를 사용한다. */
    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("stage-user", "stage@example.com", "스테이지 사용자"));
        userId = user.getId();
        userUnlockStatusRepository.saveAndFlush(UserUnlockStatus.initialFor(user));
    }

    /** 80점 미만 경계에서는 세 상태가 모두 바뀌지 않는지 검증한다. */
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 79})
    @DisplayName("80점 미만이면 게이지·해금·뱃지가 변경되지 않는다")
    void 불합격_점수는_상태를_변경하지_않는다(int totalScore) {
        ProgressGaugeResult result = stageProgressionService.applyFinalScore(userId, 1, totalScore);

        assertThat(result.changed()).isFalse();
        assertThat(result.unlockedLevel()).isEqualTo(1);
        assertThat(result.progressGauge()).isZero();
        assertThat(userBadgeRepository.countByUserId(userId)).isZero();
    }

    /** 80점 이상 경계와 범위 초과 점수가 Lv.2 해금·Stage2 지급으로 이어지는지 검증한다. */
    @ParameterizedTest
    @ValueSource(ints = {80, 100, 101})
    @DisplayName("Lv.1을 80점 이상으로 통과하면 Lv.2와 Stage2 뱃지가 열린다")
    void lv1_통과는_lv2와_stage2_뱃지를_연다(int totalScore) {
        ProgressGaugeResult result = stageProgressionService.applyFinalScore(userId, 1, totalScore);

        assertThat(result.changed()).isTrue();
        assertThat(result.unlockedLevel()).isEqualTo(2);
        assertThat(result.progressGauge()).isEqualTo(30);
        assertThat(userBadgeRepository.countByUserId(userId)).isEqualTo(1);
    }

    /** Lv.1과 Lv.2를 순서대로 통과하면 Stage2·3 뱃지가 각각 지급되는지 검증한다. */
    @Test
    @DisplayName("Lv.2까지 순서대로 통과하면 게이지 60%와 Stage2·3 뱃지를 가진다")
    void lv2_통과는_stage3_뱃지를_지급한다() {
        stageProgressionService.applyFinalScore(userId, 1, 80);
        ProgressGaugeResult result = stageProgressionService.applyFinalScore(userId, 2, 80);

        assertThat(result.unlockedLevel()).isEqualTo(3);
        assertThat(result.progressGauge()).isEqualTo(60);
        assertThat(userBadgeRepository.countByUserId(userId)).isEqualTo(2);
    }

    /** 스트레치골인 Lv.3 클리어를 직접 반영해도 MVP에서 Stage4를 지급하지 않는지 검증한다. */
    @Test
    @DisplayName("Lv.3 클리어 결과에는 MVP 범위 밖인 Stage4 뱃지를 지급하지 않는다")
    void lv3_통과는_stage4_뱃지를_지급하지_않는다() {
        stageProgressionService.applyFinalScore(userId, 1, 80);
        stageProgressionService.applyFinalScore(userId, 2, 80);
        ProgressGaugeResult result = stageProgressionService.applyFinalScore(userId, 3, 80);

        assertThat(result.unlockedLevel()).isEqualTo(4);
        assertThat(result.progressGauge()).isEqualTo(100);
        assertThat(userBadgeRepository.countByUserId(userId)).isEqualTo(2);
    }

    /** 동일 최종 판정을 재처리해도 뱃지가 중복 생성되지 않는지 검증한다. */
    @Test
    @DisplayName("동일 Stage 최종 판정을 중복 처리해도 뱃지는 한 번만 지급된다")
    void 중복_최종_판정은_뱃지를_중복_지급하지_않는다() {
        stageProgressionService.applyFinalScore(userId, 1, 80);
        ProgressGaugeResult duplicate = stageProgressionService.applyFinalScore(userId, 1, 100);

        assertThat(duplicate.changed()).isFalse();
        assertThat(userBadgeRepository.countByUserId(userId)).isEqualTo(1);
    }
}
