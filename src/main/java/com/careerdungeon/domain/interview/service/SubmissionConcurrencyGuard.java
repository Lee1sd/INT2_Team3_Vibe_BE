package com.careerdungeon.domain.interview.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** 동일 애플리케이션 인스턴스에서 같은 세션의 중복 LLM 호출을 직렬화한다. */
@Component
public class SubmissionConcurrencyGuard {

    private final Map<Long, LockHolder> sessionLocks = new HashMap<>();

    /**
     * DB 커넥션을 점유하지 않는 JVM 잠금으로 같은 세션 작업을 한 번에 하나만 실행한다.
     * 다중 인스턴스 경합의 최종 정합성은 결과 적용 시 DB 잠금과 UNIQUE 제약이 방어한다.
     */
    public <T> T execute(long sessionId, Supplier<T> action) {
        LockHolder holder = register(sessionId);
        holder.lock.lock();
        try {
            return action.get();
        } finally {
            holder.lock.unlock();
            unregister(sessionId, holder);
        }
    }

    /** 잠금 사용자를 먼저 등록해 잠금 해제와 신규 요청 사이의 제거 경쟁을 막는다. */
    private synchronized LockHolder register(long sessionId) {
        LockHolder holder = sessionLocks.computeIfAbsent(sessionId, ignored -> new LockHolder());
        holder.users++;
        return holder;
    }

    /** 마지막 사용자까지 작업을 마친 세션 잠금만 맵에서 제거한다. */
    private synchronized void unregister(long sessionId, LockHolder holder) {
        holder.users--;
        if (holder.users == 0) {
            sessionLocks.remove(sessionId, holder);
        }
    }

    /** 세션별 재진입 잠금과 현재 등록 사용자 수를 함께 관리한다. */
    private static final class LockHolder {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }
}
