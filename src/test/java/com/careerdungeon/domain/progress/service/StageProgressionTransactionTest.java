package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.exception.BadgeNotFoundException;
import com.careerdungeon.domain.progress.model.ProgressGaugeResult;
import com.careerdungeon.domain.progress.repository.BadgeRepository;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 독립 트랜잭션으로 해금·게이지·뱃지의 롤백과 동시성 불변식을 검증한다. */
@DataJpaTest
@Import({StageProgressionService.class, ProgressGaugeService.class, BadgeAwardService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StageProgressionTransactionTest {

    @Autowired
    StageProgressionService stageProgressionService;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    BadgeRepository badgeRepository;

    @Autowired
    UserBadgeRepository userBadgeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate transactionTemplate;
    long userId;

    /** 각 테스트가 커밋된 가입 직후 상태와 Stage2 기준 데이터에서 시작하도록 준비한다. */
    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        String identifier = UUID.randomUUID().toString();
        userId = transactionTemplate.execute(status -> {
            // 커밋형 테스트 사이에 남은 데이터를 FK 자식부터 제거해 실행 순서 의존성을 없앤다.
            userBadgeRepository.deleteAll();
            userUnlockStatusRepository.deleteAll();
            badgeRepository.deleteAll();
            userRepository.deleteAll();
            User user = userRepository.saveAndFlush(new User(
                    "stage-transaction-" + identifier,
                    identifier + "@example.com",
                    "스테이지 트랜잭션 사용자"));
            userUnlockStatusRepository.saveAndFlush(UserUnlockStatus.initialFor(user));
            badgeRepository.saveAndFlush(Badge.create(
                    2,
                    "Stage2 트랜잭션 테스트 뱃지",
                    "badges/Level2.png"));
            return user.getId();
        });
    }

    /** 뱃지 기준 데이터 누락 시 앞선 게이지와 해금 변경까지 롤백되는지 검증한다. */
    @Test
    @DisplayName("뱃지 지급 실패 시 게이지와 해금 변경도 함께 롤백된다")
    void 뱃지_지급_실패는_전체_변경을_롤백한다() {
        transactionTemplate.executeWithoutResult(status -> badgeRepository.deleteAll());

        assertThatThrownBy(() -> stageProgressionService.applyFinalScore(userId, 1, 80))
                .isInstanceOf(BadgeNotFoundException.class);

        UserUnlockStatus status = userUnlockStatusRepository.findById(userId).orElseThrow();
        assertThat(status.getUnlockedLevel()).isEqualTo(1);
        assertThat(status.getProgressGauge()).isZero();
        assertThat(userBadgeRepository.countByUserId(userId)).isZero();
    }

    /** 동일 통과 요청이 동시에 처리돼도 진행도와 뱃지가 한 번만 변경되는지 검증한다. */
    @Test
    @DisplayName("동일 Stage 동시 통과 요청은 해금과 뱃지를 한 번만 반영한다")
    void 동일_stage_동시_요청은_한_번만_반영한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ProgressGaugeResult> first = executor.submit(() -> applyScoreAfterSignal(ready, start));
            Future<ProgressGaugeResult> second = executor.submit(() -> applyScoreAfterSignal(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> changedResults = List.of(
                    first.get(10, TimeUnit.SECONDS).changed(),
                    second.get(10, TimeUnit.SECONDS).changed());
            assertThat(changedResults).containsExactlyInAnyOrder(true, false);

            UserUnlockStatus status = userUnlockStatusRepository.findById(userId).orElseThrow();
            assertThat(status.getUnlockedLevel()).isEqualTo(2);
            assertThat(status.getProgressGauge()).isEqualTo(30);
            assertThat(userBadgeRepository.countByUserId(userId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 두 작업을 맞춰 시작한 뒤 각각 독립 트랜잭션에서 최종 점수를 반영한다. */
    private ProgressGaugeResult applyScoreAfterSignal(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitStartSignal(start);
        return stageProgressionService.applyFinalScore(userId, 1, 80);
    }

    /** 동시성 테스트 시작 신호를 기다리며 인터럽트 상태를 보존한다. */
    private void awaitStartSignal(CountDownLatch start) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 시작 신호를 기다리는 시간이 초과됐습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }
}
