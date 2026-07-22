package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.interview.dto.InterviewDetailMessageResponse;
import com.careerdungeon.domain.interview.dto.InterviewDetailResponse;
import com.careerdungeon.domain.interview.dto.InterviewHistoryLevelResponse;
import com.careerdungeon.domain.interview.dto.InterviewHistoryResponse;
import com.careerdungeon.domain.interview.dto.InterviewHistorySessionResponse;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.InterviewSessionStatus;
import com.careerdungeon.domain.interview.repository.InterviewHistoryRow;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.judgment.entity.JudgmentResult;
import com.careerdungeon.domain.judgment.repository.JudgmentResultRepository;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InterviewHistoryService {

    private final InterviewSessionRepository interviewSessionRepository;
    private final MessageRepository messageRepository;
    private final JudgmentResultRepository judgmentResultRepository;

    public InterviewHistoryService(
            InterviewSessionRepository interviewSessionRepository,
            MessageRepository messageRepository,
            JudgmentResultRepository judgmentResultRepository) {
        this.interviewSessionRepository = interviewSessionRepository;
        this.messageRepository = messageRepository;
        this.judgmentResultRepository = judgmentResultRepository;
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

    @Transactional(readOnly = true)
    public InterviewDetailResponse getDetail(Long userId, Long sessionId) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "INTERVIEW_SESSION_NOT_FOUND",
                        "면접 세션을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(
                    "INTERVIEW_SESSION_FORBIDDEN",
                    "본인의 면접 세션만 조회할 수 있습니다.",
                    HttpStatus.FORBIDDEN);
        }
        if (session.getStatus() != InterviewSessionStatus.COMPLETED) {
            throw new BusinessException(
                    "INTERVIEW_SESSION_INVALID_STATUS",
                    "완료된 면접 세션만 상세 조회할 수 있습니다.",
                    HttpStatus.CONFLICT);
        }

        JudgmentResult judgmentResult = judgmentResultRepository.findBySession_Id(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "JUDGMENT_RESULT_NOT_FOUND",
                        "면접 최종 결과를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));

        List<Message> messages = messageRepository.findAllBySession_IdAndRoleInOrderByTurnAscRoleAsc(
                sessionId,
                List.of(MessageRole.QUESTION, MessageRole.ANSWER));
        return new InterviewDetailResponse(
                sessionId,
                conversation(messages),
                judgmentResult.getOverallFeedback());
    }

    private List<InterviewDetailMessageResponse> conversation(List<Message> messages) {
        Map<Integer, ConversationTurn> byTurn = new LinkedHashMap<>();
        for (Message message : messages) {
            ConversationTurn turn = byTurn.computeIfAbsent(message.getTurn(), ignored -> new ConversationTurn());
            if (message.getRole() == MessageRole.QUESTION) {
                turn.question = message.getContent();
            }
            if (message.getRole() == MessageRole.ANSWER) {
                turn.answer = message.getContent();
            }
        }
        return byTurn.entrySet().stream()
                .map(entry -> new InterviewDetailMessageResponse(
                        entry.getKey(),
                        entry.getValue().question,
                        entry.getValue().answer))
                .toList();
    }

    private static class ConversationTurn {
        private String question;
        private String answer;
    }
}
