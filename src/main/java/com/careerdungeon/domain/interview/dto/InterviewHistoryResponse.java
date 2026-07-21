package com.careerdungeon.domain.interview.dto;

import java.util.List;

public record InterviewHistoryResponse(
        List<InterviewHistoryLevelResponse> levels
) {
}
