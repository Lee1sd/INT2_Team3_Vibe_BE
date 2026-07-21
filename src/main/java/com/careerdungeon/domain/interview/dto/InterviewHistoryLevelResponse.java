package com.careerdungeon.domain.interview.dto;

import java.util.List;

public record InterviewHistoryLevelResponse(
        int level,
        List<InterviewHistorySessionResponse> sessions
) {
}
