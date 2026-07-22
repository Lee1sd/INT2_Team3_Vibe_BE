package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeSummaryResponse;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.service.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    // RS-003
    @GetMapping
    public ResponseEntity<List<ResumeSummaryResponse>> getResumes(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(resumeService.getResumes(userId));
    }

    // RS-004
    @DeleteMapping("/{resumeId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long resumeId) {
        resumeService.delete(userId, resumeId);
        return ResponseEntity.noContent().build();
    }
}
