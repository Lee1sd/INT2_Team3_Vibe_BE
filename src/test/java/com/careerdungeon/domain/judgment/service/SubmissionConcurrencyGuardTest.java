package com.careerdungeon.domain.judgment.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionConcurrencyGuardTest {

    /** 같은 세션 작업은 직렬화하되 서로 다른 요청 결과는 순서대로 반환하는지 검증한다. */
    @Test
    void serializesActionsForSameSession() throws Exception {
        SubmissionConcurrencyGuard guard = new SubmissionConcurrencyGuard();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger activeActions = new AtomicInteger();
        AtomicInteger maximumActiveActions = new AtomicInteger();

        try {
            Future<String> first = executor.submit(() -> guard.execute(1L, () -> {
                recordActiveCount(activeActions, maximumActiveActions);
                firstEntered.countDown();
                await(releaseFirst);
                activeActions.decrementAndGet();
                return "first";
            }));
            assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();

            Future<String> second = executor.submit(() -> guard.execute(1L, () -> {
                recordActiveCount(activeActions, maximumActiveActions);
                activeActions.decrementAndGet();
                return "second";
            }));

            Thread.sleep(100);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();

            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(maximumActiveActions).hasValue(1);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    /** 동시 실행 중인 작업 수의 최댓값을 원자적으로 기록한다. */
    private void recordActiveCount(AtomicInteger activeActions, AtomicInteger maximumActiveActions) {
        int active = activeActions.incrementAndGet();
        maximumActiveActions.accumulateAndGet(active, Math::max);
    }

    /** 테스트 작업 내부의 인터럽트를 실패로 전환해 동시성 실패 원인을 보존한다. */
    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }
}
