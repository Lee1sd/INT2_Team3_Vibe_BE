package com.careerdungeon.domain.interview.dto;

public record InterviewerResponse(
        Long id,
        String name,
        int level,
        String tone,
        boolean unlocked,
        boolean comingSoon) {
}
