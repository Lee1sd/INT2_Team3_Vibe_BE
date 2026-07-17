package com.careerdungeon.domain.interview.controller;

import com.careerdungeon.domain.interview.dto.InterviewCreateRequest;
import com.careerdungeon.domain.interview.dto.InterviewCreateResponse;
import com.careerdungeon.domain.interview.service.InterviewService;
import com.careerdungeon.domain.judgment.dto.AnswerSubmissionRequest;
import com.careerdungeon.domain.judgment.dto.AnswerSubmissionResponse;
import com.careerdungeon.domain.judgment.service.AnswerSubmissionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final AnswerSubmissionService answerSubmissionService;

    /** 면접 생성과 답변 제출 도메인 서비스를 연결한다. */
    public InterviewController(
            InterviewService interviewService,
            AnswerSubmissionService answerSubmissionService) {
        this.interviewService = interviewService;
        this.answerSubmissionService = answerSubmissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewCreateResponse createInterview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InterviewCreateRequest request) {
        return interviewService.createInterview(userId, request);
    }

    /** 세션 상태에 따라 최초 세 답변 채점 또는 꼬리질문 최종 판정을 실행한다. */
    @PostMapping("/{id}/answers")
    public AnswerSubmissionResponse submitAnswers(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long sessionId,
            @Valid @RequestBody AnswerSubmissionRequest request) {
        return answerSubmissionService.submit(userId, sessionId, request);
    }
}
