package com.careerdungeon.global.exception;

import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.global.llm.exception.LlmProviderConfigException;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler(userRepository))
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void typeMismatch_withInvalidValue_returns400() throws Exception {
        mockMvc.perform(get("/test/type-mismatch"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("파라미터 'type'의 값이 올바르지 않습니다: INVALID"));
    }

    @Test
    void typeMismatch_withNullValue_returns400WithEmptyLabel() throws Exception {
        mockMvc.perform(get("/test/type-mismatch-null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"))
                .andExpect(jsonPath("$.message").value("파라미터 'type'의 값이 올바르지 않습니다: (빈 값)"));
    }

    @Test
    void missingParam_returns400() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUIRED_PARAMETER"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("필수 파라미터 'type'가 누락되었습니다."));
    }

    @Test
    void llmProviderConfig_returnsSanitizedResponse() throws Exception {
        mockMvc.perform(get("/test/llm-provider-config"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_PROVIDER_CONFIG_ERROR"))
                .andExpect(jsonPath("$.message").value("LLM 설정을 확인해 주세요."));
    }

    @Test
    void llmFailureLogContext_containsOnlySanitizedCauseMetadata() {
        var rawCause = new LlmSchemaValidationException(
                "raw response body: resume text and prompt content",
                null,
                429);
        var exception = new LlmPermanentFailureException("public failure", rawCause);

        GlobalExceptionHandler.LlmFailureLogContext context =
                GlobalExceptionHandler.LlmFailureLogContext.from(exception);

        assertThat(context.correlationId()).isNotBlank();
        assertThat(context.causeType()).isEqualTo("LlmSchemaValidationException");
        assertThat(context.providerStatus()).isEqualTo("429");
        assertThat(context.toString()).doesNotContain("resume text", "prompt content", "raw response body");
    }

    // 이슈 #107 — 탈퇴한 유저의 accessToken으로 쓰기 API를 호출하면 user_id FK 위반이
    // 발생하는데, 이걸 401로 좁혀 바꾸되 인증된 유저가 실제로 존재하면(다른 원인의 FK
    // 위반) 기존처럼 500을 유지하는지 확인한다.
    @Test
    void dataIntegrityViolation_withDeletedAuthenticatedUser_returns401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList()));
        given(userRepository.existsById(1L)).willReturn(false);

        mockMvc.perform(get("/test/data-integrity-violation"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("인증된 사용자 계정을 찾을 수 없습니다. 다시 로그인해 주세요."))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void dataIntegrityViolation_withExistingAuthenticatedUser_returns500() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList()));
        given(userRepository.existsById(1L)).willReturn(true);

        mockMvc.perform(get("/test/data-integrity-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void dataIntegrityViolation_withoutAuthentication_returns500() throws Exception {
        mockMvc.perform(get("/test/data-integrity-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/type-mismatch")
        void typeMismatch() {
            throw new MethodArgumentTypeMismatchException(
                    "INVALID", Enum.class, "type", null, null);
        }

        @GetMapping("/test/type-mismatch-null")
        void typeMismatchNull() {
            throw new MethodArgumentTypeMismatchException(
                    null, Enum.class, "type", null, null);
        }

        @GetMapping("/test/missing-param")
        void missingParam() throws MissingServletRequestParameterException {
            throw new MissingServletRequestParameterException("type", "String");
        }

        @GetMapping("/test/llm-provider-config")
        void llmProviderConfig() {
            throw new LlmProviderConfigException("provider said raw credential failure body", 401);
        }

        @GetMapping("/test/data-integrity-violation")
        void dataIntegrityViolation() {
            throw new DataIntegrityViolationException("simulated FK violation");
        }
    }
}