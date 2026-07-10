package com.careerdungeon.global.llm.mock;

import com.careerdungeon.global.config.RetryConfig;
import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.EvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * mock 모드에서 IS-002b 꼬리질문 채점 흐름을 검증하는 통합 테스트.
 * MockLlmClient + LlmResponseValidator + LlmInvocationService (Spring Retry 포함) 실제 연동.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RetryConfig.class,
        LlmResponseValidator.class,
        LlmInvocationService.class,
        LlmMockModeIntegrationTest.TestConfig.class
})
class LlmMockModeIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        LlmClient llmClient() {
            return new MockLlmClient(18);
        }
    }

    @Autowired
    LlmInvocationService sut;

    @Test
    @DisplayName("mock 모드 IS-002b: 꼬리질문 채점 요청 → validateFinalEvaluation 통과, 3개 evaluations 반환")
    void followUpEvaluation_mockMode_passesValidationAndReturnsThreeEvaluations() {
        var request = EvaluationRequest.followUp(
                new QuestionAnswerPair(4, "꼬리질문", "꼬리 답변", "모범답변"),
                "STRICT", "홍길동", Set.of(1, 2));

        assertThatCode(() -> {
            EvaluationResponse response = sut.evaluateAnswers(request);

            assertThat(response.evaluations()).hasSize(3);
            assertThat(response.evaluations()).extracting("turn").containsExactlyInAnyOrder(1, 2, 4);
            assertThat(response.evaluations().stream()
                    .filter(e -> e.turn() == 4).findFirst().orElseThrow().feedback())
                    .isNotBlank();
            assertThat(response.weakestQuestionId()).isEqualTo(0);
        }).doesNotThrowAnyException();
    }
}
