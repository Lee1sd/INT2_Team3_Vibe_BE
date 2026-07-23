package com.careerdungeon.domain.progress.entity;

import com.careerdungeon.domain.progress.model.BadgeUnlockCondition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

/** Stage별 뱃지의 표시 정보와 지급 조건을 보관한다. */
@Entity
@Table(
        name = "badges",
        uniqueConstraints = @UniqueConstraint(name = "UK_badges_stage", columnNames = "stage"))
@Check(constraints = "stage between 1 and 4")
public class Badge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int stage;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "image_url", nullable = false, length = 255)
    private String imageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "unlock_condition", nullable = false, length = 50)
    private BadgeUnlockCondition unlockCondition;

    /** JPA가 뱃지 엔티티를 복원할 때 사용하는 기본 생성자다. */
    protected Badge() {
    }

    /** 검증된 표시 정보와 지급 조건으로 뱃지 기준 데이터를 구성한다. */
    private Badge(int stage, String name, String imageKey, BadgeUnlockCondition unlockCondition) {
        this.stage = stage;
        this.name = requireText(name, "뱃지 이름");
        this.imageKey = requireImageKeyForStage(stage, imageKey);
        this.unlockCondition = unlockCondition;
    }

    /** 확정된 Stage 매핑과 S3 object key를 적용해 뱃지 기준 데이터를 생성한다. */
    public static Badge create(int stage, String name, String imageKey) {
        return new Badge(stage, name, imageKey, BadgeUnlockCondition.fromStage(stage));
    }

    /** Stage별로 허용된 고정 뱃지 이미지 object key를 반환한다. */
    public static String expectedImageKeyForStage(int stage) {
        BadgeUnlockCondition.fromStage(stage);
        return "badges/Level" + stage + ".png";
    }

    /** 필수 문자열이 비어 있는 기준 데이터 생성을 차단한다. */
    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
        return value;
    }

    /** Stage와 다른 이미지에 서명하거나 정적 경로를 노출할 수 없도록 key 일치를 검증한다. */
    private static String requireImageKeyForStage(int stage, String imageKey) {
        String requiredImageKey = requireText(imageKey, "뱃지 이미지 S3 키");
        String expectedImageKey = expectedImageKeyForStage(stage);
        if (!expectedImageKey.equals(requiredImageKey)) {
            throw new IllegalArgumentException(
                    "Stage " + stage + " 뱃지 이미지 S3 키는 " + expectedImageKey + "이어야 합니다.");
        }
        return requiredImageKey;
    }

    /** 뱃지 식별자를 반환한다. */
    public Long getId() {
        return id;
    }

    /** 뱃지 Stage를 반환한다. */
    public int getStage() {
        return stage;
    }

    /** 사용자에게 표시할 뱃지 이름을 반환한다. */
    public String getName() {
        return name;
    }

    /** Presigned GET URL 생성에 사용할 private S3 object key를 반환한다. */
    public String getImageKey() {
        return imageKey;
    }

    /** 뱃지 지급 조건을 반환한다. */
    public BadgeUnlockCondition getUnlockCondition() {
        return unlockCondition;
    }
}
