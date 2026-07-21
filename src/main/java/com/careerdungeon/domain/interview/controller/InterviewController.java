package com.careerdungeon.domain.interview.controller;

import com.careerdungeon.domain.interview.dto.InterviewCreateRequest;
import com.careerdungeon.domain.interview.dto.InterviewCreateResponse;
import com.careerdungeon.domain.interview.dto.InterviewAnswerSubmitRequest;
import com.careerdungeon.domain.interview.dto.InterviewAnswerSubmitResponse;
import com.careerdungeon.domain.interview.dto.InterviewHistoryResponse;
import com.careerdungeon.domain.interview.service.InterviewHistoryService;
import com.careerdungeon.domain.interview.service.InterviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewHistoryService interviewHistoryService;

    public InterviewController(
            InterviewService interviewService,
            InterviewHistoryService interviewHistoryService) {
        this.interviewService = interviewService;
        this.interviewHistoryService = interviewHistoryService;
    }

    @GetMapping("/history")
    public InterviewHistoryResponse getHistory(@AuthenticationPrincipal Long userId) {
        return interviewHistoryService.getHistory(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewCreateResponse createInterview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InterviewCreateRequest request) {
        return interviewService.createInterview(userId, request);
    }

    @PostMapping("/{id}/answers")
    public InterviewAnswerSubmitResponse submitAnswers(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long sessionId,
            @Valid @RequestBody InterviewAnswerSubmitRequest request) {
        return interviewService.submitAnswers(userId, sessionId, request);
    }
}
