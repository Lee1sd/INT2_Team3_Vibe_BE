package com.careerdungeon.global.llm.mock;

import com.careerdungeon.global.config.RetryConfig;
import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
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
    @DisplayName("mock 모드 IS-002b: turn 5 단독 채점 요청이 검증을 통과하고 평가 한 건을 반환한다")
    void finalEvaluation_mockMode_passesValidationAndReturnsFollowUpEvaluation() {
        var pairs = List.of(new QuestionAnswerPair(5, "꼬리질문", "꼬리 답변", "모범답변"));
        var contexts = List.of(
                new PreviousEvaluationContext(1, "질문1", "답변1", 20, "기술 선택 근거가 좋습니다."),
                new PreviousEvaluationContext(2, "질문2", "답변2", 10, "예외 상황 보완이 필요합니다."),
                new PreviousEvaluationContext(3, "질문3", "답변3", 19, "구체성이 좋습니다."),
                new PreviousEvaluationContext(4, "질문4", "답변4", 16, "피드백4"));
        var request = EvaluationRequest.finalEvaluation(pairs, contexts, "STRICT", "홍길동");

        assertThatCode(() -> {
            FinalEvaluationResponse response = sut.evaluateFinalAnswers(request);

            assertThat(response.evaluations()).hasSize(1);
            assertThat(response.evaluations()).extracting("turn").containsExactly(5);
            assertThat(response.evaluations().get(0).feedback()).isNotBlank();
            assertThat(response.overallFeedback()).isNotBlank();
            assertThat(response.overallFeedback())
                    .contains("turn 2", "예외 상황 보완")
                    .contains("🎯 총평", "✨ 이런 점이 매우 훌륭했어요")
                    .contains("🚀 합격을 확정 짓는 2%", "💡 Next Step")
                    .contains("❌ AS-IS", "⭕ TO-BE");
        }).doesNotThrowAnyException();
    }
}
