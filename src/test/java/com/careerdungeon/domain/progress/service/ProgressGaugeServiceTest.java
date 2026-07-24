package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.exception.InvalidStageProgressionException;
import com.careerdungeon.domain.progress.exception.UserProgressNotFoundException;
import com.careerdungeon.domain.progress.model.ProgressGaugeResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 JPA 저장소와 트랜잭션을 사용해 최종 점수의 게이지 반영 경계를 검증한다. */
@DataJpaTest
@Import(ProgressGaugeService.class)
class ProgressGaugeServiceTest {

    @Autowired
    ProgressGaugeService progressGaugeService;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    UserRepository userRepository;

    long userId;

    /** 각 테스트가 가입 직후 Stage1 오픈·게이지 0% 상태에서 시작하도록 준비한다. */
    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("progress-user", "progress@example.com", "진행도 사용자"));
        userId = user.getId();
        userUnlockStatusRepository.saveAndFlush(UserUnlockStatus.initialFor(user));
    }

    /** Lv.1 통과 기준 60점 미만에서는 진행도 상태가 전혀 변경되지 않는지 검증한다. */
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 59})
    @DisplayName("Lv.1 최종 점수가 60점 미만이면 신뢰도 게이지가 증가하지 않는다")
    void 불합격_점수는_게이지를_변경하지_않는다(int totalScore) {
        ProgressGaugeResult result = progressGaugeService.applyFinalScore(userId, 1, totalScore);

        assertThat(result.changed()).isFalse();
        assertThat(result.unlockedLevel()).isEqualTo(1);
        assertThat(result.progressGauge()).isZero();
    }

    /** Lv.1 60점 경계와 범위 초과 점수를 서버에서 보정해 통과 처리하는지 검증한다. */
    @ParameterizedTest
    @ValueSource(ints = {60, 100, 101})
    @DisplayName("Lv.1 최종 점수가 60점 이상이면 Stage1 게이지를 30%로 반영한다")
    void 합격_점수는_stage1_게이지를_반영한다(int totalScore) {
        ProgressGaugeResult result = progressGaugeService.applyFinalScore(userId, 1, totalScore);

        assertThat(result.changed()).isTrue();
        assertThat(result.unlockedLevel()).isEqualTo(2);
        assertThat(result.progressGauge()).isEqualTo(30);
    }

    /** 동일 Stage 통과 결과가 재전달돼도 누적 게이지가 한 번만 증가하는지 검증한다. */
    @Test
    @DisplayName("동일 Stage의 최종 판정을 중복 처리해도 게이지는 한 번만 증가한다")
    void 동일_stage_중복_처리는_멱등하다() {
        ProgressGaugeResult first = progressGaugeService.applyFinalScore(userId, 1, 60);
        ProgressGaugeResult duplicate = progressGaugeService.applyFinalScore(userId, 1, 100);

        assertThat(first.changed()).isTrue();
        assertThat(duplicate.changed()).isFalse();
        assertThat(duplicate.unlockedLevel()).isEqualTo(2);
        assertThat(duplicate.progressGauge()).isEqualTo(30);
    }

    /** Lv.2는 기존 80점 통과 기준을 유지하는지 검증한다. */
    @Test
    @DisplayName("Lv.2는 79점에 실패하고 80점에 통과한다")
    void lv2_통과_기준은_80점이다() {
        progressGaugeService.applyFinalScore(userId, 1, 60);

        ProgressGaugeResult failed = progressGaugeService.applyFinalScore(userId, 2, 79);
        ProgressGaugeResult passed = progressGaugeService.applyFinalScore(userId, 2, 80);

        assertThat(failed.changed()).isFalse();
        assertThat(passed.changed()).isTrue();
        assertThat(passed.unlockedLevel()).isEqualTo(3);
        assertThat(passed.progressGauge()).isEqualTo(60);
    }

    /** Stage별 정책을 연속 적용해 100% 상한과 다음 Stage 순서를 검증한다. */
    @Test
    @DisplayName("Stage1, Stage2, Stage3을 클리어하면 누적 게이지가 최대 100%가 된다")
    void 모든_stage를_클리어하면_게이지가_100이_된다() {
        progressGaugeService.applyFinalScore(userId, 1, 60);
        progressGaugeService.applyFinalScore(userId, 2, 80);
        ProgressGaugeResult result = progressGaugeService.applyFinalScore(userId, 3, 80);

        assertThat(result.changed()).isTrue();
        assertThat(result.unlockedLevel()).isEqualTo(4);
        assertThat(result.progressGauge()).isEqualTo(100);
    }

    /** 순차 위반 예외가 발생했을 때 트랜잭션 내 상태가 바뀌지 않는지 검증한다. */
    @Test
    @DisplayName("Stage를 건너뛰면 예외가 발생하고 기존 진행도 상태를 유지한다")
    void stage_건너뛰기_실패는_상태를_변경하지_않는다() {
        assertThatThrownBy(() -> progressGaugeService.applyFinalScore(userId, 2, 80))
                .isInstanceOf(InvalidStageProgressionException.class);

        UserUnlockStatus status = userUnlockStatusRepository.findById(userId).orElseThrow();
        assertThat(status.getUnlockedLevel()).isEqualTo(1);
        assertThat(status.getProgressGauge()).isZero();
    }

    /** 가입 이벤트가 진행도를 만들지 않은 사용자의 점수 반영을 거부하는지 검증한다. */
    @Test
    @DisplayName("사용자 진행도 상태가 없으면 404 도메인 예외가 발생한다")
    void 진행도_상태가_없는_사용자를_거부한다() {
        assertThatThrownBy(() -> progressGaugeService.applyFinalScore(999L, 1, 60))
                .isInstanceOf(UserProgressNotFoundException.class);
    }
}
