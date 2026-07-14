package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.model.ProgressGaugeResult;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
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

/** 독립 트랜잭션을 사용해 게이지 갱신의 롤백·동시성 계약을 검증한다. */
@DataJpaTest
@Import(ProgressGaugeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProgressGaugeTransactionTest {

    @Autowired
    ProgressGaugeService progressGaugeService;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate transactionTemplate;
    long userId;

    /** 각 테스트가 자동 테스트 트랜잭션 밖에서 커밋된 초기 진행도를 사용하도록 준비한다. */
    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        String identifier = UUID.randomUUID().toString();
        userId = transactionTemplate.execute(status -> {
            User user = userRepository.saveAndFlush(new User(
                    "progress-transaction-" + identifier,
                    identifier + "@example.com",
                    "트랜잭션 사용자"));
            userUnlockStatusRepository.saveAndFlush(UserUnlockStatus.initialFor(user));
            return user.getId();
        });
    }

    /** 상위 작업 실패 시 이미 수행한 게이지 변경까지 함께 롤백되는지 검증한다. */
    @Test
    @DisplayName("게이지 변경 후 상위 작업이 실패하면 전체 트랜잭션이 롤백된다")
    void 상위_작업_실패는_게이지_변경도_롤백한다() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            progressGaugeService.applyFinalScore(userId, 1, 80);
            throw new IllegalStateException("후속 뱃지 처리 실패");
        })).isInstanceOf(IllegalStateException.class);

        UserUnlockStatus status = userUnlockStatusRepository.findById(userId).orElseThrow();
        assertThat(status.getUnlockedLevel()).isEqualTo(1);
        assertThat(status.getProgressGauge()).isZero();
    }

    /** 상위 트랜잭션 없이 호출하면 MANDATORY 계약이 즉시 실패하는지 검증한다. */
    @Test
    @DisplayName("상위 트랜잭션 없이 게이지 서비스를 호출할 수 없다")
    void 상위_트랜잭션을_필수로_요구한다() {
        assertThatThrownBy(() -> progressGaugeService.applyFinalScore(userId, 1, 80))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    /** 동일 Stage를 동시에 처리해도 비관적 잠금으로 한 번만 변경되는지 검증한다. */
    @Test
    @DisplayName("동일 Stage 동시 통과 요청은 게이지를 한 번만 변경한다")
    void 동일_stage_동시_요청은_한_번만_반영한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<ProgressGaugeResult> first = executor.submit(
                    () -> applyScoreAfterSignal(ready, start));
            Future<ProgressGaugeResult> second = executor.submit(
                    () -> applyScoreAfterSignal(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Boolean> changedResults = List.of(
                    first.get(10, TimeUnit.SECONDS).changed(),
                    second.get(10, TimeUnit.SECONDS).changed());
            assertThat(changedResults).containsExactlyInAnyOrder(true, false);

            UserUnlockStatus status = userUnlockStatusRepository.findById(userId).orElseThrow();
            assertThat(status.getUnlockedLevel()).isEqualTo(2);
            assertThat(status.getProgressGauge()).isEqualTo(30);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 두 작업이 동시에 시작하도록 대기한 뒤 각자 독립 트랜잭션에서 점수를 반영한다. */
    private ProgressGaugeResult applyScoreAfterSignal(CountDownLatch ready, CountDownLatch start) {
        return transactionTemplate.execute(status -> {
            ready.countDown();
            awaitStartSignal(start);
            return progressGaugeService.applyFinalScore(userId, 1, 80);
        });
    }

    /** 동시성 테스트의 시작 신호를 기다리며 인터럽트 상태를 보존한다. */
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
