package com.careerdungeon.domain.auth.controller;

import com.careerdungeon.domain.auth.dto.ProfileImageResponse;
import com.careerdungeon.domain.auth.service.RefreshTokenCookieFactory;
import com.careerdungeon.domain.auth.service.UserService;
import com.careerdungeon.global.exception.BusinessException;
import com.careerdungeon.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이슈 #98/ADR-018 — UP-004/UP-005 컨트롤러가 UserService에 올바르게 위임하고,
 * ProfileImageStorageService가 던지는 BusinessException(MIME 거부/용량 초과)이
 * GlobalExceptionHandler를 거쳐 올바른 HTTP 상태로 매핑되는지 확인한다.
 * ResumeControllerTest와 같은 패턴(@WebMvcTest + addFilters=false + 수동 SecurityContext)을
 * 쓴다 — S3Client 등 실제 인프라 빈을 띄우지 않고 서비스 레이어만 목으로 대체한다.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final Long TEST_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RefreshTokenCookieFactory cookieFactory;

    @MockitoBean
    private JwtProvider jwtProvider;

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(TEST_USER_ID, null, Collections.emptyList()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/users/me/photo: 정상 업로드 시 200과 photoUrl 반환")
    void uploadProfileImage_success() throws Exception {
        given(userService.updateProfileImage(anyLong(), any()))
                .willReturn(new ProfileImageResponse("https://bucket.s3.amazonaws.com/profile-images/1/a.jpg?X-Amz-Signature=..."));

        MockMultipartFile file = new MockMultipartFile("photo", "me.jpg", "image/jpeg", "dummy-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value("https://bucket.s3.amazonaws.com/profile-images/1/a.jpg?X-Amz-Signature=..."));
    }

    @Test
    @DisplayName("POST /api/users/me/photo: 지원하지 않는 MIME이면 400")
    void uploadProfileImage_unsupportedMime_returns400() throws Exception {
        given(userService.updateProfileImage(anyLong(), any()))
                .willThrow(new BusinessException(
                        "PROFILE_IMAGE_UNSUPPORTED_TYPE", "지원하지 않는 이미지 형식입니다. (jpeg, png, webp만 허용)",
                        HttpStatus.BAD_REQUEST));

        MockMultipartFile file = new MockMultipartFile("photo", "me.gif", "image/gif", "dummy-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_UNSUPPORTED_TYPE"));
    }

    @Test
    @DisplayName("POST /api/users/me/photo: 용량 초과면 413")
    void uploadProfileImage_tooLarge_returns413() throws Exception {
        given(userService.updateProfileImage(anyLong(), any()))
                .willThrow(new BusinessException(
                        "PROFILE_IMAGE_TOO_LARGE", "이미지 용량은 2MB를 초과할 수 없습니다.",
                        HttpStatus.PAYLOAD_TOO_LARGE));

        MockMultipartFile file = new MockMultipartFile("photo", "big.png", "image/png", "dummy-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo").file(file))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PROFILE_IMAGE_TOO_LARGE"));
    }

    @Test
    @DisplayName("DELETE /api/users/me/photo: 정상 삭제 시 200")
    void deleteProfileImage_success() throws Exception {
        mockMvc.perform(delete("/api/users/me/photo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("프로필 이미지가 삭제되었습니다"));
    }
}
