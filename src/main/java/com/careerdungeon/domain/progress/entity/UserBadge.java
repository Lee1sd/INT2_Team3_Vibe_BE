package com.careerdungeon.domain.progress.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** 사용자가 획득한 뱃지와 획득 시각을 보관한다. */
@Entity
@Table(
        name = "user_badges",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_user_badges_user_badge",
                columnNames = {"user_id", "badge_id"}))
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "badge_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_badges_TO_user_badges_1"))
    private Badge badge;

    @CreationTimestamp
    @Column(name = "acquired_at", nullable = false, updatable = false)
    private Instant acquiredAt;

    /** JPA가 사용자 뱃지 엔티티를 복원할 때 사용하는 기본 생성자다. */
    protected UserBadge() {
    }

    /** 유효한 사용자와 저장된 뱃지를 연결해 획득 레코드를 구성한다. */
    private UserBadge(long userId, Badge badge) {
        if (userId <= 0) {
            throw new IllegalArgumentException("유효한 사용자 식별자가 필요합니다.");
        }
        if (badge == null || badge.getId() == null) {
            throw new IllegalArgumentException("저장된 뱃지 기준 데이터가 필요합니다.");
        }
        this.userId = userId;
        this.badge = badge;
    }

    /** 저장된 뱃지를 사용자에게 지급할 획득 레코드를 생성한다. */
    public static UserBadge award(long userId, Badge badge) {
        return new UserBadge(userId, badge);
    }

    /** 사용자 뱃지 식별자를 반환한다. */
    public Long getId() {
        return id;
    }

    /** 뱃지를 획득한 사용자 식별자를 반환한다. */
    public Long getUserId() {
        return userId;
    }

    /** 사용자가 획득한 뱃지를 반환한다. */
    public Badge getBadge() {
        return badge;
    }

    /** 뱃지 획득 시각을 반환한다. */
    public Instant getAcquiredAt() {
        return acquiredAt;
    }
}
