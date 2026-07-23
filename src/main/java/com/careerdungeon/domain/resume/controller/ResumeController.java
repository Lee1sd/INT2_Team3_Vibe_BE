package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeSummaryResponse;
import com.careerdungeon.domain.resume.dto.ResumeUploadCompleteRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlResponse;
import com.careerdungeon.domain.resume.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping("/upload-url")
    public ResponseEntity<ResumeUploadUrlResponse> issueUploadUrl(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody ResumeUploadUrlRequest request) {
        return ResponseEntity.ok(resumeService.issueUploadUrl(userId, request));
    }

    @PostMapping("/upload-complete")
    public ResponseEntity<ResumeResponse> completeUpload(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody ResumeUploadCompleteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(resumeService.completeUpload(userId, request));
    }

    @GetMapping("/{resumeId}")
    public ResponseEntity<ResumeResponse> getStatus(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long resumeId) {
        return ResponseEntity.ok(resumeService.getStatus(userId, resumeId));
    }

    @GetMapping
    public ResponseEntity<List<ResumeSummaryResponse>> getResumes(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(resumeService.getResumes(userId));
    }

    @DeleteMapping("/{resumeId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long resumeId) {
        resumeService.delete(userId, resumeId);
        return ResponseEntity.noContent().build();
    }
}
