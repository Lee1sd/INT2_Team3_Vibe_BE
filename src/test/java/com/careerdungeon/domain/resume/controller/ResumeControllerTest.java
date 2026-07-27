package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeSummaryResponse;
import com.careerdungeon.domain.resume.dto.ResumeUploadCompleteRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlRequest;
import com.careerdungeon.domain.resume.dto.ResumeUploadUrlResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.exception.ResumeObjectVersionMismatchException;
import com.careerdungeon.domain.resume.service.ResumeService;
import com.careerdungeon.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ResumeControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ResumeService resumeService;
    @MockitoBean JwtProvider jwtProvider;

    // GlobalExceptionHandler(@RestControllerAdvice)가 이슈 #107로 UserRepository를
    // 요구하게 되어, 이 슬라이스 컨텍스트에도 목으로 채워야 뜬다.
    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void issuesPresignedUrl() throws Exception {
        var request = new ResumeUploadUrlRequest(ResumeType.RESUME, "resume.pdf", 100, "application/pdf");
        given(resumeService.issueUploadUrl(1L, request))
                .willReturn(new ResumeUploadUrlResponse("https://upload", "resumes/1/pending/id.pdf", 300));

        mockMvc.perform(post("/api/resumes/upload-url").contentType("application/json")
                        .content("{\"type\":\"RESUME\",\"fileName\":\"resume.pdf\",\"fileSize\":100,\"contentType\":\"application/pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://upload"))
                .andExpect(jsonPath("$.s3Key").value("resumes/1/pending/id.pdf"));
    }

    @Test
    void listResumesSerializesIsLastUsedFieldNameAsIs() throws Exception {
        given(resumeService.getResumes(1L)).willReturn(List.of(
                new ResumeSummaryResponse(501L, ResumeType.RESUME, ParseStatus.DONE,
                        Instant.parse("2026-07-15T10:00:00Z"), "resume.pdf", 1048576L, true)
        ));

        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].resumeId").value(501))
                .andExpect(jsonPath("$[0].isLastUsed").value(true));
    }

    @Test
    void completesUpload() throws Exception {
        var request = new ResumeUploadCompleteRequest(
                ResumeType.RESUME, "resumes/1/pending/id.pdf", "resume.pdf");
        given(resumeService.completeUpload(1L, request))
                .willReturn(new ResumeResponse(
                        501L, ResumeType.RESUME, ParseStatus.PROCESSING,
                        null, "resume.pdf", 1048576L));

        mockMvc.perform(post("/api/resumes/upload-complete").contentType("application/json")
                        .content("""
                                {"type":"RESUME","s3Key":"resumes/1/pending/id.pdf",
                                 "originalFileName":"resume.pdf"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeId").value(501))
                .andExpect(jsonPath("$.parseStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.originalFileName").value("resume.pdf"))
                .andExpect(jsonPath("$.fileSize").value(1048576));
        verify(resumeService).completeUpload(1L, request);
    }

    @Test
    void acceptsCompletionWithoutOriginalFilenameUsingFallback() throws Exception {
        var request = new ResumeUploadCompleteRequest(
                ResumeType.RESUME, "resumes/1/pending/id.pdf", null);
        given(resumeService.completeUpload(1L, request))
                .willReturn(new ResumeResponse(
                        501L, ResumeType.RESUME, ParseStatus.PROCESSING,
                        null, "이력서.pdf", 1048576L));

        mockMvc.perform(post("/api/resumes/upload-complete").contentType("application/json")
                        .content("""
                                {"type":"RESUME","s3Key":"resumes/1/pending/id.pdf"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumeId").value(501))
                .andExpect(jsonPath("$.originalFileName").value("이력서.pdf"));
        verify(resumeService).completeUpload(1L, request);
    }

    @Test
    void returnsConflictWhenUploadedObjectVersionChangesDuringCompletion() throws Exception {
        var request = new ResumeUploadCompleteRequest(
                ResumeType.RESUME, "resumes/1/pending/id.pdf", "resume.pdf");
        given(resumeService.completeUpload(1L, request))
                .willThrow(new ResumeObjectVersionMismatchException(new RuntimeException("etag mismatch")));

        mockMvc.perform(post("/api/resumes/upload-complete").contentType("application/json")
                        .content("""
                                {"type":"RESUME","s3Key":"resumes/1/pending/id.pdf",
                                 "originalFileName":"resume.pdf"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESUME_OBJECT_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
