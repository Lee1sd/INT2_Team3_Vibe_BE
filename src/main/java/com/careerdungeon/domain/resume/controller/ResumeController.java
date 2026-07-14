package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.service.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    // RS-001
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeResponse> upload(
            @RequestParam ResumeType type,
            @RequestParam("file") MultipartFile file) {
        // TODO: Security 설정 완료 후 인증된 사용자 정보(userId)로 교체
        Long userId = 1L;

        ResumeResponse response = resumeService.upload(userId, type, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // RS-002
    @GetMapping("/{resumeId}")
    public ResponseEntity<ResumeResponse> getStatus(@PathVariable Long resumeId) {
        // TODO: Security 설정 완료 후 인증된 사용자 정보(userId)로 교체
        Long userId = 1L;

        ResumeResponse response = resumeService.getStatus(userId, resumeId);
        return ResponseEntity.ok(response);
    }
}
