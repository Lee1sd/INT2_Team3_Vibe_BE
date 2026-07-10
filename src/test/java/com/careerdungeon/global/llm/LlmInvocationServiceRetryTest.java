package com.careerdungeon.global.llm;

import com.careerdungeon.global.config.RetryConfig;
import com.careerdungeon.global.exception.LlmPermanentFailureException;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.EvaluationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Retryable 재시도 흐름 통합 검증.
 * Spring Boot 전체 컨텍스트(JPA 등) 없이 RetryConfig + LlmInvocationService + Validator만 로드.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RetryConfig.class,
        LlmResponseValidator.class,
        LlmInvocationService.class,
        LlmInvocationServiceRetryTest.TestConfig.class
})
class LlmInvocationServiceRetryTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        LlmClient llmClient() {
            return Mockito.mock(LlmClient.class);
        }
    }

    @Autowired
    LlmClient llmClient;

    @Autowired
    LlmInvocationService sut;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(llmClient);
    }

    @Test
    @DisplayName("질문 생성 응답에 중복 turn [1,1,2] → 3회 재시도 후 LlmPermanentFailureException")
    void generateQuestions_duplicateTurns_retriesAndThrows() {
        var malformedResponse = new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1, "질문1", "답1"),
                new GeneratedQuestion(1, "질문2", "답2"),
                new GeneratedQuestion(2, "질문3", "답3")
        ));
        when(llmClient.generateQuestions(any())).thenReturn(malformedResponse);

        var request = new QuestionGenerationRequest("이력서", "Java", "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.generateQuestions(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(3)).generateQuestions(any());
    }

    @Test
    @DisplayName("질문 생성 응답에 turn=4 (FR-03 위반) → 3회 재시도 후 LlmPermanentFailureException")
    void generateQuestions_turn4_retriesAndThrows() {
        var malformedResponse = new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1, "질문1", "답1"),
                new GeneratedQuestion(2, "질문2", "답2"),
                new GeneratedQuestion(4, "질문3", "답3")
        ));
        when(llmClient.generateQuestions(any())).thenReturn(malformedResponse);

        var request = new QuestionGenerationRequest("이력서", "Java", "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.generateQuestions(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(3)).generateQuestions(any());
    }

    @Test
    @DisplayName("질문 생성 응답에 null 항목 → 3회 재시도 후 LlmPermanentFailureException (NPE 아님)")
    void generateQuestions_nullElement_retriesAndThrows() {
        var questions = new ArrayList<GeneratedQuestion>();
        questions.add(new GeneratedQuestion(1, "질문1", "답1"));
        questions.add(null);
        questions.add(new GeneratedQuestion(3, "질문3", "답3"));
        when(llmClient.generateQuestions(any()))
                .thenReturn(new QuestionGenerationResponse(questions));

        var request = new QuestionGenerationRequest("이력서", "Java", "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.generateQuestions(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(3)).generateQuestions(any());
    }

    @Test
    @DisplayName("채점 응답에 중복 turn → 3회 재시도 후 LlmPermanentFailureException")
    void evaluateAnswers_duplicateTurns_retriesAndThrows() {
        var malformedResponse = new EvaluationResponse(List.of(
                new QuestionEvaluation(1, 18, "피드백1"),
                new QuestionEvaluation(1, 20, "피드백2")
        ), 38, 1, false);
        when(llmClient.evaluateAnswers(any())).thenReturn(malformedResponse);

        var request = new EvaluationRequest(List.of(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(3)).evaluateAnswers(any());
    }
}
