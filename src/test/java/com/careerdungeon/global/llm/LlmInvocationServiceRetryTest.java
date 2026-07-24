package com.careerdungeon.global.llm;

import com.careerdungeon.global.config.RetryConfig;
import com.careerdungeon.global.exception.LlmPermanentFailureException;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmProviderConfigException;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

    /** 테스트에서 루브릭 세부값은 검증 대상이 아니므로 0으로 고정한다. */
    private static QuestionEvaluation eval(int turn, int score, String feedback) {
        return new QuestionEvaluation(turn, score, 0, 0, 0, 0, 0, feedback);
    }

    @Test
    @DisplayName("1차 스키마 이탈 → 2차 정상 응답 → 재시도 복구 성공, 정상 값 반환")
    void generateQuestions_firstMalformed_secondValid_returnsResult() {
        var malformed = new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1, "질문1", "답1"),
                new GeneratedQuestion(1, "질문2", "답2"),
                new GeneratedQuestion(2, "질문3", "답3"),
                new GeneratedQuestion(3, "질문4", "답4")
        ));
        var valid = new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1, "질문1", "답1"),
                new GeneratedQuestion(2, "질문2", "답2"),
                new GeneratedQuestion(3, "질문3", "답3"),
                new GeneratedQuestion(4, "질문4", "답4")
        ));
        when(llmClient.generateQuestions(any()))
                .thenReturn(malformed)
                .thenReturn(valid);

        var request = new QuestionGenerationRequest("이력서", "Java", "STRICT", "홍길동");
        var result = sut.generateQuestions(request);

        assertThat(result).isEqualTo(valid);
        verify(llmClient, times(2)).generateQuestions(any());
    }

    @Test
    @DisplayName("질문 생성 프롬프트 전달 overload도 스키마 이탈 시 재시도한다")
    void generateQuestionsWithPrompt_firstMalformed_secondValid_returnsResult() {
        var malformed = new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1, "질문1", "답"),
                new GeneratedQuestion(1, "질문2", "답"),
                new GeneratedQuestion(2, "질문3", "답"),
                new GeneratedQuestion(3, "질문4", "답")
        ));
        var valid = new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1, "질문1", "답"),
                new GeneratedQuestion(2, "질문2", "답"),
                new GeneratedQuestion(3, "질문3", "답"),
                new GeneratedQuestion(4, "질문4", "답")
        ));
        when(llmClient.generateQuestions(any(), any(LlmPrompt.class)))
                .thenReturn(malformed)
                .thenReturn(valid);

        var request = new QuestionGenerationRequest("이력서", "Java", "STRICT", "한비");
        var prompt = new LlmPrompt("system", "user");
        var result = sut.generateQuestions(request, prompt);

        assertThat(result).isEqualTo(valid);
        verify(llmClient, times(2)).generateQuestions(any(), any(LlmPrompt.class));
    }

    @Test
    @DisplayName("질문 생성 응답에 중복 turn [1,1,2,3] → 2회 시도 후 LlmPermanentFailureException")
    void generateQuestions_duplicateTurns_retriesAndThrows() {
        var malformedResponse = new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1, "질문1", "답1"),
                new GeneratedQuestion(1, "질문2", "답2"),
                new GeneratedQuestion(2, "질문3", "답3"),
                new GeneratedQuestion(3, "질문4", "답4")
        ));
        when(llmClient.generateQuestions(any())).thenReturn(malformedResponse);

        var request = new QuestionGenerationRequest("이력서", "Java", "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.generateQuestions(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).generateQuestions(any());
    }

    @Test
    @DisplayName("질문 생성 응답에 turn=5 (FR-03 위반) → 2회 시도 후 LlmPermanentFailureException")
    void generateQuestions_turn5_retriesAndThrows() {
        var malformedResponse = new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1, "질문1", "답1"),
                new GeneratedQuestion(2, "질문2", "답2"),
                new GeneratedQuestion(3, "질문3", "답3"),
                new GeneratedQuestion(5, "질문4", "답4")
        ));
        when(llmClient.generateQuestions(any())).thenReturn(malformedResponse);

        var request = new QuestionGenerationRequest("이력서", "Java", "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.generateQuestions(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).generateQuestions(any());
    }

    @Test
    @DisplayName("질문 생성 응답에 null 항목 → 2회 시도 후 LlmPermanentFailureException (NPE 아님)")
    void generateQuestions_nullElement_retriesAndThrows() {
        var questions = new ArrayList<GeneratedQuestion>();
        questions.add(new GeneratedQuestion(1, "질문1", "답1"));
        questions.add(null);
        questions.add(new GeneratedQuestion(3, "질문3", "답3"));
        questions.add(new GeneratedQuestion(4, "질문4", "답4"));
        when(llmClient.generateQuestions(any()))
                .thenReturn(new QuestionGenerationResponse(questions));

        var request = new QuestionGenerationRequest("이력서", "Java", "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.generateQuestions(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).generateQuestions(any());
    }

    @Test
    @DisplayName("최초 4문항 채점 요청에 turn 5 혼입 응답 → 2회 시도 후 LlmPermanentFailureException")
    void evaluateInitialAnswers_unexpectedTurn5_retriesAndThrows() {
        var malformedResponse = new InitialEvaluationResponse(List.of(
                eval(1, 18, "피드백1"),
                eval(2, 20, "피드백2"),
                eval(3, 15, "피드백3"),
                eval(5, 22, "피드백5")
        ), 75, 3, false);
        when(llmClient.evaluateInitialAnswers(any())).thenReturn(malformedResponse);

        var request = EvaluationRequest.initial(List.of(
                new QuestionAnswerPair(1, "질문1", "답1", "모범1"),
                new QuestionAnswerPair(2, "질문2", "답2", "모범2"),
                new QuestionAnswerPair(3, "질문3", "답3", "모범3"),
                new QuestionAnswerPair(4, "질문4", "답4", "모범4")
        ), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateInitialAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).evaluateInitialAnswers(any());
    }

    /** 리소스 프롬프트 전달 오버로드에도 기존 최초 채점 재시도 정책이 적용되는지 검증한다. */
    @Test
    @DisplayName("최초 채점 프롬프트 전달 overload도 스키마 이탈 시 재시도한다")
    void evaluateInitialAnswersWithPrompt_malformedResponse_retriesAndThrows() {
        var malformedResponse = new InitialEvaluationResponse(List.of(
                eval(1, 18, "피드백1"),
                eval(1, 20, "피드백2")
        ), 38, 1, false);
        when(llmClient.evaluateInitialAnswers(any(), any(LlmPrompt.class))).thenReturn(malformedResponse);

        var request = EvaluationRequest.initial(List.of(
                new QuestionAnswerPair(1, "질문1", "답1", "모범1"),
                new QuestionAnswerPair(2, "질문2", "답2", "모범2"),
                new QuestionAnswerPair(3, "질문3", "답3", "모범3")
        ), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateInitialAnswers(request, new LlmPrompt("system", "user")))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).evaluateInitialAnswers(any(), any(LlmPrompt.class));
    }

    @Test
    @DisplayName("IS-002b 최종 응답에 turn 5 외 문항이 섞이면 2회 시도 후 영구 실패한다")
    void evaluateFinalAnswers_extraResponseTurn_retriesAndThrows() {
        var malformedResponse = new FinalEvaluationResponse(List.of(
                eval(1, 20, "이전 문항 피드백"),
                eval(5, 22, "꼬리질문 피드백")
        ), 42, false, "종합 피드백");
        when(llmClient.evaluateFinalAnswers(any())).thenReturn(malformedResponse);

        var pairs = List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변"));
        var request = EvaluationRequest.finalEvaluation(pairs, previousContexts(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).evaluateFinalAnswers(any());
    }

    /** 리소스 프롬프트 전달 오버로드에도 기존 최종 채점 재시도 정책이 적용되는지 검증한다. */
    @Test
    @DisplayName("최종 채점 프롬프트 전달 overload도 스키마 이탈 시 재시도한다")
    void evaluateFinalAnswersWithPrompt_extraResponseTurn_retriesAndThrows() {
        var malformedResponse = new FinalEvaluationResponse(List.of(
                eval(1, 20, "이전 문항 피드백"),
                eval(5, 22, "꼬리질문 피드백")
        ), 42, false, "종합 피드백");
        when(llmClient.evaluateFinalAnswers(any(), any(LlmPrompt.class))).thenReturn(malformedResponse);

        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request, new LlmPrompt("system", "user")))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).evaluateFinalAnswers(any(), any(LlmPrompt.class));
    }

    @Test
    @DisplayName("최종 리포트 형식 이탈은 1회 재시도 후 정상 응답으로 복구한다")
    void evaluateFinalAnswers_invalidCareerReportRetriesAndRecovers() {
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "꼬리질문 피드백")),
                18,
                false,
                "일반 문장형 종합 피드백");
        var validResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "꼬리질문 피드백")),
                18,
                false,
                validCareerReport());
        when(llmClient.evaluateFinalAnswers(any()))
                .thenReturn(malformedResponse)
                .thenReturn(validResponse);

        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = sut.evaluateFinalAnswers(request);
        assertThat(actual.evaluations()).isEqualTo(validResponse.evaluations());
        assertThat(actual.totalScore()).isEqualTo(validResponse.totalScore());
        assertThat(actual.passed()).isEqualTo(validResponse.passed());
        // 서버가 가상 수치 고지를 리포트 끝에 항상 덧붙이므로 원본 그대로는 비교하지 않는다.
        assertThat(actual.overallFeedback())
                .startsWith(validResponse.overallFeedback().stripTrailing())
                .endsWith("※ 아래 수치는 답변 구조를 보여주기 위한 가상 예시이며, 실제 측정 결과가 아닙니다.");
        verify(llmClient, times(2)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("Claude 인증·요청 설정 오류는 재시도나 ExhaustedRetryException 변환 없이 전파한다")
    void evaluateFinalAnswers_providerConfigFailurePropagatesWithoutRetry() {
        LlmProviderConfigException providerFailure =
                new LlmProviderConfigException("Claude API request is not retryable: HTTP 401", 401);
        when(llmClient.evaluateFinalAnswers(any(), any(LlmPrompt.class)))
                .thenThrow(providerFailure);
        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request, new LlmPrompt("system", "user")))
                .isSameAs(providerFailure)
                .isInstanceOfSatisfying(
                        LlmProviderConfigException.class,
                        exception -> assertThat(exception.statusCode()).isEqualTo(401));
        verify(llmClient, times(1))
                .evaluateFinalAnswers(any(), any(LlmPrompt.class));
    }

    @Test
    @DisplayName("IS-002b 요청인데 questionAnswerPairs 비어있음 → 재시도 없이 즉시 LlmPermanentFailureException")
    void evaluateFinalAnswers_emptyPairs_failsImmediatelyWithoutRetry() {
        var request = new EvaluationRequest(List.of(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        // 요청 turn 구성 검증이 LLM 호출 이전에 수행됨 — LLM 호출 자체가 발생하지 않는다
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("IS-002b 문항 목록이 null이면 NPE 대신 계약 예외로 즉시 실패한다")
    void evaluateFinalAnswers_nullPairs_failsWithContractException() {
        EvaluationRequest request = Mockito.mock(EvaluationRequest.class);
        when(request.questionAnswerPairs()).thenReturn(null);

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class)
                .hasMessageContaining("null 문항");
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("IS-002b 이전 평가 컨텍스트 목록이 null이면 NPE 대신 계약 예외로 즉시 실패한다")
    void evaluateFinalAnswers_nullPreviousContexts_failsWithContractException() {
        EvaluationRequest request = Mockito.mock(EvaluationRequest.class);
        when(request.questionAnswerPairs()).thenReturn(List.of(
                new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")));
        when(request.previousEvaluations()).thenReturn(null);

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class)
                .hasMessageContaining("이전 평가 컨텍스트");
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("요청 turn 검증 실패는 LlmSchemaValidationException이 아니므로 @Retryable 대상이 아니다 — 500ms 백오프 없이 즉시 실패 (코드래빗 지적)")
    void evaluateFinalAnswers_requestValidationFailure_doesNotTriggerRetryBackoff() {
        var request = new EvaluationRequest(List.of(), "STRICT", "홍길동");

        long startNanos = System.nanoTime();
        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        // 재시도됐다면 500ms 백오프가 최소 1회(500ms 이상) 발생했을 것 — 그보다 훨씬 짧아야 한다
        assertThat(elapsedMs).isLessThan(500);
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("IS-002b 요청이 turn 5 한 건이 아니면 재시도 없이 즉시 실패한다")
    void evaluateFinalAnswers_missingFollowUpTurn_failsImmediatelyWithoutRetry() {
        var pairs = List.of(new QuestionAnswerPair(3, "질문3", "답3", "모범3"));
        var request = EvaluationRequest.finalEvaluation(pairs, previousContexts(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("IS-002b 요청에 turn 5가 중복되면 size 불일치로 재시도 없이 즉시 실패한다")
    void evaluateFinalAnswers_duplicatePairInflatesSize_failsImmediatelyWithoutRetry() {
        var pairs = List.of(
                new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변"),
                new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")
        );
        var request = EvaluationRequest.finalEvaluation(pairs, previousContexts(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("IS-002b turn 5의 필수 문자열이 누락되면 LLM 호출 없이 즉시 실패한다")
    void evaluateFinalAnswers_blankRequiredField_failsImmediatelyWithoutRetry() {
        var request = EvaluationRequest.finalEvaluation(List.of(
                new QuestionAnswerPair(5, "꼬리질문", "답변", " ")),
                previousContexts(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class)
                .hasMessageContaining("모범답변");
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("IS-002b 이전 평가 컨텍스트가 누락되면 LLM 호출 없이 즉시 실패한다")
    void evaluateFinalAnswers_missingPreviousContext_failsImmediatelyWithoutRetry() {
        var request = EvaluationRequest.finalEvaluation(List.of(
                new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                List.of(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class)
                .hasMessageContaining("이전 평가 컨텍스트");
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    @ParameterizedTest(name = "이전 평가 점수 {0}은 LLM 호출 전에 거부한다")
    @ValueSource(ints = {-1, 21})
    @DisplayName("IS-002b 이전 평가 점수가 0~20 범위를 벗어나면 즉시 실패한다")
    void evaluateFinalAnswers_previousScoreOutOfRange_failsImmediatelyWithoutRetry(int invalidScore) {
        var invalidContexts = List.of(
                new PreviousEvaluationContext(1, "질문1", "답변1", 20, "피드백1"),
                new PreviousEvaluationContext(2, "질문2", "답변2", invalidScore, "피드백2"),
                new PreviousEvaluationContext(3, "질문3", "답변3", 19, "피드백3"),
                new PreviousEvaluationContext(4, "질문4", "답변4", 18, "피드백4"));
        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                invalidContexts,
                "STRICT",
                "홍길동");

        assertThatThrownBy(() -> sut.evaluateFinalAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class)
                .hasMessageContaining("점수");
        verify(llmClient, times(0)).evaluateFinalAnswers(any());
    }

    /** 런타임 출력 계약 테스트에 사용할 정상 최종 커리어 리포트를 반환한다. */
    private String validCareerReport() {
        return """
                🎯 총평
                판단 근거는 좋았지만 운영 지표로 효과를 증명하는 설명은 부족했습니다.

                ✨ 이런 점이 매우 훌륭했어요
                - JOIN FETCH의 적용 범위를 구분했습니다.
                - 캐시 정합성 보완 전략을 설명했습니다.

                🚀 합격을 확정 짓는 2%
                부하 테스트 결과를 근거와 연결하세요.

                💡 Next Step
                ❌ AS-IS (지원자의 기존 답변 방식)
                캐시를 삭제해 정합성을 맞췄습니다.

                ⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)
                적용 전후를 [예: p95 응답 시간 320ms → 140ms]로 비교하세요.
                """;
    }

    @Test
    @DisplayName("채점 응답에 중복 turn → 2회 시도 후 LlmPermanentFailureException")
    void evaluateInitialAnswers_duplicateTurns_retriesAndThrows() {
        var malformedResponse = new InitialEvaluationResponse(List.of(
                eval(1, 18, "피드백1"),
                eval(1, 20, "피드백2")
        ), 38, 1, false);
        when(llmClient.evaluateInitialAnswers(any())).thenReturn(malformedResponse);

        var request = EvaluationRequest.initial(List.of(), "STRICT", "홍길동");

        assertThatThrownBy(() -> sut.evaluateInitialAnswers(request))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).evaluateInitialAnswers(any());
    }

    @Test
    @DisplayName("꼬리질문 생성 1차 스키마 이탈 → 2차 정상 응답 → 재시도 복구 성공")
    void generateFollowUp_firstMalformed_secondValid_returnsResult() {
        var malformed = new FollowUpGenerationResponse(" ", "모범답안");
        var valid = new FollowUpGenerationResponse("꼬리질문", "모범답안");
        when(llmClient.generateFollowUp(anyInt(), any(), any(), any()))
                .thenReturn(malformed)
                .thenReturn(valid);

        var result = sut.generateFollowUp(2, "질문", "답변", "피드백");

        assertThat(result).isEqualTo(valid);
        verify(llmClient, times(2)).generateFollowUp(anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("꼬리질문 생성 응답에 expectedAnswer 없음 → 2회 시도 후 LlmPermanentFailureException")
    void generateFollowUp_blankExpectedAnswer_retriesAndThrows() {
        var malformed = new FollowUpGenerationResponse("꼬리질문", " ");
        when(llmClient.generateFollowUp(anyInt(), any(), any(), any()))
                .thenReturn(malformed);

        assertThatThrownBy(() -> sut.generateFollowUp(2, "질문", "답변", "피드백"))
                .isInstanceOf(LlmPermanentFailureException.class);
        verify(llmClient, times(2)).generateFollowUp(anyInt(), any(), any(), any());
    }

    /** 최종 피드백용 정상 최초 평가 컨텍스트를 생성한다. */
    private static List<PreviousEvaluationContext> previousContexts() {
        return List.of(
                new PreviousEvaluationContext(1, "질문1", "답변1", 20, "피드백1"),
                new PreviousEvaluationContext(2, "질문2", "답변2", 15, "피드백2"),
                new PreviousEvaluationContext(3, "질문3", "답변3", 19, "피드백3"),
                new PreviousEvaluationContext(4, "질문4", "답변4", 18, "피드백4"));
    }
}
