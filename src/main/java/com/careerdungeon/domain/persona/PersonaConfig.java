package com.careerdungeon.domain.persona;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * 면접관 페르소나 설정. MVP 범위는 Lv.1(널널한 대리)/Lv.2(깐깐한 과장) 2종만 지원한다.
 *
 * <p>Lv.3(압박 페르소나)은 스트레치골로 이 엔티티가 다루지 않는다 — 프론트엔드에서
 * {@code comingSoon=true} placeholder로만 노출된다(이슈 #17,
 * docs/state/invariants-and-state-machines.md §4).
 */
@Entity
@Table(name = "persona_config")
public class PersonaConfig {

    private static final int MIN_LEVEL = 1;
    private static final int MAX_SUPPORTED_LEVEL = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonaTone tone;

    protected PersonaConfig() {
    }

    public PersonaConfig(int level, PersonaTone tone) {
        if (level < MIN_LEVEL || level > MAX_SUPPORTED_LEVEL) {
            throw new IllegalArgumentException(
                    "PersonaConfig.level은 " + MIN_LEVEL + "~" + MAX_SUPPORTED_LEVEL
                            + " 범위만 지원합니다 (Lv.3은 스트레치골, 이슈 #17 범위 밖): " + level);
        }
        this.level = level;
        this.tone = Objects.requireNonNull(tone, "tone must not be null");
    }

    public Long getId() {
        return id;
    }

    public int getLevel() {
        return level;
    }

    public PersonaTone getTone() {
        return tone;
    }
}
