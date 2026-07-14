package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.service.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    // TODO(표지민): type이 잘못된 값이거나 누락되면 MethodArgumentTypeMismatchException/
    // MissingServletRequestParameterException이 발생하는데, GlobalExceptionHandler에 이 둘을
    // 처리하는 핸들러가 없어 catch-all로 떨어져 400이 아니라 500이 반환된다.
    // global/exception/GlobalExceptionHandler에 핸들러 추가 필요 — 표지민 소유 경로라 직접 수정하지 않음.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeResponse> upload(
            @AuthenticationPrincipal Long userId,
            @RequestParam ResumeType type,
            @RequestParam("file") MultipartFile file) {
        ResumeResponse response = resumeService.upload(userId, type, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // RS-002
    @GetMapping("/{resumeId}")
    public ResponseEntity<ResumeResponse> getStatus(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long resumeId) {
        ResumeResponse response = resumeService.getStatus(userId, resumeId);
        return ResponseEntity.ok(response);
    }
}
