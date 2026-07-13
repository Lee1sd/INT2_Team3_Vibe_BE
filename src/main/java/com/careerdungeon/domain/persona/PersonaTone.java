package com.careerdungeon.domain.persona;

/**
 * 면접관 페르소나의 톤. API 레벨에서는 소문자(lenient/strict)로 노출된다
 * (docs/state/invariants-and-state-machines.md §4).
 */
public enum PersonaTone {
    LENIENT,
    STRICT;

    public String apiValue() {
        return name().toLowerCase();
    }
}
