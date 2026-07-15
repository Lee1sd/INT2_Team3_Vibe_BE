package com.careerdungeon.domain.progress.repository;

import com.careerdungeon.domain.progress.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자별 뱃지 획득 레코드의 저장과 중복 확인을 담당한다. */
public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    /** 동일 사용자가 같은 뱃지를 이미 획득했는지 확인한다. */
    boolean existsByUserIdAndBadgeId(long userId, long badgeId);

    /** 사용자가 획득한 전체 뱃지 수를 반환한다. */
    long countByUserId(long userId);
}
