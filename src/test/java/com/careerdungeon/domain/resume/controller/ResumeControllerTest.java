package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.dto.ResumeSummaryResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.exception.ResumeNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.service.ResumeService;
import com.careerdungeon.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// GlobalExceptionHandler(@RestControllerAdvice)는 @WebMvcTest에 자동 포함되므로
// BusinessException 하위 예외가 이 핸들러에서 정상적으로 잡히는지도 함께 검증한다.
// SecurityFilterChain은 addFilters=false로 제외하지만, SecurityConfig가 @Configuration으로
// 슬라이스에 함께 로드되면서 JwtAuthenticationFilter(Filter 구현체)가 딸려 들어와 JwtProvider 빈이
// 필요해진다 — 실제로 인증을 태우는 게 아니라 컨텍스트 기동을 위한 최소 목이다.
@WebMvcTest(ResumeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ResumeControllerTest {

    private static final Long TEST_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeService resumeService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUpAuthentication() {
        // JwtAuthenticationFilter가 필터 체인에서 하는 일(Long userId를 principal로 SecurityContext에
        // 세팅)을 필터 없이 재현한다. addFilters=false라 실제 필터는 안 타지만 @AuthenticationPrincipal은
        // 여전히 SecurityContextHolder를 읽으므로 이게 없으면 userId가 항상 null로 들어간다.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, null, Collections.emptyList()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/resumes: 정상 업로드 시 201과 ResumeResponse 반환")
    void upload_success() throws Exception {
        given(resumeService.upload(anyLong(), any(ResumeType.class), any()))
                .willReturn(ResumeResponse.uploaded(501L, ResumeType.RESUME, ParseStatus.PROCESSING));

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "dummy-pdf-content".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/resumes")
                        .file(file)
                        .param("type", "RESUME"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.resumeId").value(501))
                .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("RESUME"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.parseStatus").value("PROCESSING"));
    }

    @Test
    @DisplayName("POST /api/resumes: 동일 type 3개 초과 업로드 시 ResumeTypeLimitExceededException -> 400")
    void upload_typeLimitExceeded_returns400() throws Exception {
        given(resumeService.upload(anyLong(), any(ResumeType.class), any()))
                .willThrow(new ResumeTypeLimitExceededException(ResumeType.RESUME));

        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "dummy-pdf-content".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/resumes")
                        .file(file)
                        .param("type", "RESUME"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("RESUME_TYPE_LIMIT_EXCEEDED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/resumes: type이 enum에 없는 값이면 공통 에러 포맷으로 400 반환")
    void upload_invalidType_returns400WithErrorContract() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "dummy-pdf-content".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/resumes")
                        .file(file)
                        .param("type", "INVALID"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value("파라미터 'type'의 값이 올바르지 않습니다: INVALID"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(400));

        verifyNoInteractions(resumeService);
    }

    @Test
    @DisplayName("GET /api/resumes/{resumeId}: 정상 조회 시 200과 ResumeResponse 반환")
    void getStatus_success() throws Exception {
        given(resumeService.getStatus(anyLong(), org.mockito.ArgumentMatchers.eq(501L)))
                .willReturn(new ResumeResponse(501L, ResumeType.RESUME, ParseStatus.DONE, "masked text"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/resumes/{resumeId}", 501L))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.resumeId").value(501))
                .andExpect(MockMvcResultMatchers.jsonPath("$.parseStatus").value("DONE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.extractedText").value("masked text"));
    }

    @Test
    @DisplayName("GET /api/resumes/{resumeId}: 존재하지 않는 resumeId 조회 시 ResumeNotFoundException -> 404")
    void getStatus_notFound_returns404() throws Exception {
        given(resumeService.getStatus(anyLong(), org.mockito.ArgumentMatchers.eq(999L)))
                .willThrow(new ResumeNotFoundException(999L));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/resumes/{resumeId}", 999L))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("RESUME_NOT_FOUND"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/resumes: 로그인 사용자의 전체 이력서 목록을 최신순으로 반환")
    void getResumes_success() throws Exception {
        Instant newestCreatedAt = Instant.parse("2026-07-16T10:00:00Z");
        Instant oldestCreatedAt = Instant.parse("2026-07-15T10:00:00Z");
        given(resumeService.getResumes(TEST_USER_ID)).willReturn(List.of(
                new ResumeSummaryResponse(502L, ResumeType.PORTFOLIO, ParseStatus.FAILED, newestCreatedAt),
                new ResumeSummaryResponse(501L, ResumeType.RESUME, ParseStatus.DONE, oldestCreatedAt)
        ));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/resumes"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].resumeId").value(502))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].parseStatus").value("FAILED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].lastUploadedAt").value("2026-07-16T10:00:00Z"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].resumeId").value(501))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].parseStatus").value("DONE"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].lastUploadedAt").value("2026-07-15T10:00:00Z"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].extractedText").doesNotExist());

        verify(resumeService).getResumes(TEST_USER_ID);
    }
}
