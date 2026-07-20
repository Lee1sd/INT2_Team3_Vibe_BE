package com.careerdungeon.domain.interview.controller;

import com.careerdungeon.domain.interview.dto.InterviewerListResponse;
import com.careerdungeon.domain.interview.service.InterviewerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviewers")
public class InterviewerController {

    private final InterviewerService interviewerService;

    public InterviewerController(InterviewerService interviewerService) {
        this.interviewerService = interviewerService;
    }

    @GetMapping
    public InterviewerListResponse listInterviewers(@AuthenticationPrincipal Long userId) {
        return interviewerService.listInterviewers(userId);
    }
}
