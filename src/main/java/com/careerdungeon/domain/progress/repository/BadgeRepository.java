package com.careerdungeon.domain.progress.repository;

import com.careerdungeon.domain.progress.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Stage별 뱃지 기준 데이터의 저장과 조회를 담당한다. */
public interface BadgeRepository extends JpaRepository<Badge, Long> {

    /** 지급할 뱃지 기준 데이터를 Stage로 조회한다. */
    Optional<Badge> findByStage(int stage);
}
