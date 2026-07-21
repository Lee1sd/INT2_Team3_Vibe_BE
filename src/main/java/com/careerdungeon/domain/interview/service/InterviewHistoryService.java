package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.interview.dto.InterviewHistoryLevelResponse;
import com.careerdungeon.domain.interview.dto.InterviewHistoryResponse;
import com.careerdungeon.domain.interview.dto.InterviewHistorySessionResponse;
import com.careerdungeon.domain.interview.repository.InterviewHistoryRow;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class InterviewHistoryService {

    private final InterviewSessionRepository interviewSessionRepository;

    public InterviewHistoryService(InterviewSessionRepository interviewSessionRepository) {
        this.interviewSessionRepository = interviewSessionRepository;
    }

    @Transactional(readOnly = true)
    public InterviewHistoryResponse getHistory(Long userId) {
        List<InterviewHistoryRow> rows = interviewSessionRepository.findCompletedHistoryByUserId(userId);
        List<InterviewHistoryLevelResponse> levels = new ArrayList<>();
        Integer currentLevel = null;
        List<InterviewHistorySessionResponse> currentSessions = new ArrayList<>();

        for (InterviewHistoryRow row : rows) {
            if (currentLevel == null || currentLevel != row.level()) {
                if (currentLevel != null) {
                    levels.add(new InterviewHistoryLevelResponse(currentLevel, List.copyOf(currentSessions)));
                    currentSessions.clear();
                }
                currentLevel = row.level();
            }
            currentSessions.add(new InterviewHistorySessionResponse(
                    row.sessionId(),
                    row.createdAt(),
                    row.totalScore()));
        }

        if (currentLevel != null) {
            levels.add(new InterviewHistoryLevelResponse(currentLevel, List.copyOf(currentSessions)));
        }
        return new InterviewHistoryResponse(List.copyOf(levels));
    }
}
