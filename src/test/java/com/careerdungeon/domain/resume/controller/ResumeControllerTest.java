package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.resume.dto.ResumeResponse;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.exception.ResumeNotFoundException;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.service.ResumeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

// GlobalExceptionHandler(@RestControllerAdvice)는 @WebMvcTest에 자동 포함되므로
// BusinessException 하위 예외가 이 핸들러에서 정상적으로 잡히는지도 함께 검증한다.
@WebMvcTest(ResumeController.class)
@AutoConfigureMockMvc(addFilters = false) // 인증 미구현(TODO) 단계 - Security 필터는 컨트롤러 테스트 범위에서 제외
class ResumeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeService resumeService;

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
}
