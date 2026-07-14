package com.careerdungeon.domain.progress.repository;

import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 사용자 진행도 상태의 저장과 동시 갱신 잠금을 담당한다. */
public interface UserUnlockStatusRepository extends JpaRepository<UserUnlockStatus, Long> {

    /** 동일 사용자의 중복 통과 처리를 직렬화하기 위해 쓰기 잠금으로 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select status from UserUnlockStatus status where status.userId = :userId")
    Optional<UserUnlockStatus> findByUserIdForUpdate(@Param("userId") long userId);
}
