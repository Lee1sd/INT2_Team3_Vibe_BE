package com.careerdungeon.global.exception;

import com.careerdungeon.global.llm.exception.LlmProviderConfigException;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
    }
}
