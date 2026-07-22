package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.exception.BadgeNotFoundException;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 가입 초기화 중 뱃지 지급 실패가 진행도 생성까지 롤백하는지 검증한다. */
@DataJpaTest
@Import({SignupProgressService.class, BadgeAwardService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SignupProgressTransactionTest {

    @Autowired
    SignupProgressService signupProgressService;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    UserBadgeRepository userBadgeRepository;

    @Autowired
    BadgeRepository badgeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    User user;

    /** 뱃지 기준 데이터 없이 커밋된 신규 사용자만 준비한다. */
    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        String identifier = UUID.randomUUID().toString();
        user = transactionTemplate.execute(status -> {
            // 커밋형 테스트 사이에 남은 데이터를 FK 자식부터 제거해 실행 순서 의존성을 없앤다.
            userBadgeRepository.deleteAll();
            userUnlockStatusRepository.deleteAll();
            badgeRepository.deleteAll();
            userRepository.deleteAll();
            return userRepository.saveAndFlush(new User(
                    "signup-rollback-" + identifier,
                    identifier + "@example.com",
                    "가입 롤백 사용자"));
        });
    }

    /** Stage1 기준 데이터가 없으면 초기 진행도와 뱃지가 모두 남지 않는지 검증한다. */
    @Test
    @DisplayName("Stage1 뱃지 지급 실패 시 가입 초기 진행도 생성도 롤백된다")
    void stage1_뱃지_누락은_가입_초기화를_롤백한다() {
        assertThatThrownBy(() -> signupProgressService.initializeFor(user.getId()))
                .isInstanceOf(BadgeNotFoundException.class);

        assertThat(userUnlockStatusRepository.findById(user.getId())).isEmpty();
        assertThat(userBadgeRepository.countByUserId(user.getId())).isZero();
    }

    /** 동일 가입 신호가 동시에 도착해도 초기 상태와 뱃지가 한 건인지 검증한다. */
    @Test
    @DisplayName("동일 가입 신호의 동시 처리는 진행도와 Stage1 뱃지를 한 번만 생성한다")
    void 동일_가입_신호_동시_처리는_멱등하다() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> badgeRepository.saveAndFlush(
                Badge.create(1, "Stage1 동시성 테스트 뱃지", "badges/Level1.png")));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> initializeAfterSignal(ready, start));
            Future<?> second = executor.submit(() -> initializeAfterSignal(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertThat(userUnlockStatusRepository.count()).isEqualTo(1);
            assertThat(userBadgeRepository.countByUserId(user.getId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 두 가입 작업을 맞춰 시작한 뒤 각각 서비스 트랜잭션에서 초기화한다. */
    private void initializeAfterSignal(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        awaitStartSignal(start);
        signupProgressService.initializeFor(user.getId());
    }

    /** 동시성 테스트 시작 신호를 기다리며 인터럽트 상태를 보존한다. */
    private void awaitStartSignal(CountDownLatch start) {
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("가입 동시성 테스트 시작 신호를 기다리는 시간이 초과됐습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("가입 동시성 테스트 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }
}
