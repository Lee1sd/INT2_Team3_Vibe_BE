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
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.careerdungeon.global.llm.validation.CareerReportValidator;
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

    /** 재시도 통합 테스트가 제어할 LLM Mock 빈을 제공한다. */
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
    @DisplayName("최종 리포트 형식 이탈은 1회 재요청하고, 재요청도 실패하면 실제 면접 리포트로 대체하며 점수는 보존한다")
    void evaluateFinalAnswers_invalidCareerReportRetriesOnceThenFallsBack() {
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "꼬리질문 피드백")),
                18,
                false,
                "일반 문장형 종합 피드백");
        var malformedRetryResponse = new FinalEvaluationResponse(
                List.of(eval(5, 20, "꼬리질문 피드백 - 재요청")),
                20,
                true,
                "재요청도 여전히 일반 문장형");
        when(llmClient.evaluateFinalAnswers(any()))
                .thenReturn(malformedResponse)
                .thenReturn(malformedRetryResponse);

        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = sut.evaluateFinalAnswers(request);

        // 리포트 재요청은 점수를 다시 매기지 않는다 — 최초 응답의 점수가 그대로 보존된다.
        assertThat(actual.evaluations()).isEqualTo(malformedResponse.evaluations());
        assertThat(actual.totalScore()).isEqualTo(malformedResponse.totalScore());
        assertThat(actual.passed()).isEqualTo(malformedResponse.passed());
        // 재요청도 실패하면 고정 사과문이 아니라 실제 질문·답변·확정 피드백 기반 리포트를 반환한다.
        assertContextualReport(actual.overallFeedback(), "꼬리질문", "답변", "꼬리질문 피드백");
        // 최초 호출 + 재요청 1회 = 총 2회.
        verify(llmClient, times(2)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("리포트 형식이 10회 연속 반복 이탈해도 10개 모두 실제 면접 4섹션 리포트를 반환한다")
    void evaluateFinalAnswers_tenRepeatedReportFailuresReturnTenContextualReports() {
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "꼬리질문에서 재시도 전략은 설명했지만 관측 지표가 부족합니다.")),
                18,
                false,
                "형식이 깨진 일반 문장형 리포트");
        when(llmClient.evaluateFinalAnswers(any())).thenReturn(malformedResponse);
        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(
                        5,
                        "캐시 정합성 실패를 어떻게 복구합니까?",
                        "DB 커밋 뒤 캐시를 삭제하고 실패하면 큐에서 재시도합니다.",
                        "재시도와 멱등성 및 경쟁 조건을 설명한다.")),
                previousContexts(),
                "STRICT",
                "홍길동");
        CareerReportValidator reportValidator = new CareerReportValidator();
        List<FinalEvaluationResponse> responses = new ArrayList<>();

        for (int sample = 0; sample < 10; sample++) {
            responses.add(sut.evaluateFinalAnswers(request));
        }

        assertThat(responses).hasSize(10).allSatisfy(response -> {
            assertThat(reportValidator.isValid(response.overallFeedback())).isTrue();
            assertContextualReport(
                    response.overallFeedback(),
                    "캐시 정합성 실패를 어떻게 복구합니까",
                    "DB 커밋 뒤 캐시를 삭제하고 실패하면 큐에서 재시도합니다",
                    "관측 지표가 부족합니다");
            assertThat(response.totalScore()).isEqualTo(18);
        });
        // 표본마다 최초 호출과 허용된 리포트 재요청 1회만 수행한다.
        verify(llmClient, times(20)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("리포트 재요청 자체가 스키마 오류여도 실제 면접 리포트를 반환하며 점수는 보존한다")
    void evaluateFinalAnswers_invalidCareerReportRetryThrowsSchemaException_fallsBackWithoutPropagating() {
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "꼬리질문 피드백")),
                18,
                false,
                "일반 문장형 종합 피드백");
        when(llmClient.evaluateFinalAnswers(any()))
                .thenReturn(malformedResponse)
                .thenThrow(new LlmSchemaValidationException("재요청 응답 JSON 파싱 실패"));

        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = sut.evaluateFinalAnswers(request);

        // 재요청 자체가 예외를 던져도 evaluateFinalAnswers를 감싼 바깥쪽 @Retryable/@Recover까지
        // 전파되지 않는다 — 전파됐다면 LlmPermanentFailureException이 나며 점수까지 유실됐을 것이다.
        assertThat(actual.evaluations()).isEqualTo(malformedResponse.evaluations());
        assertThat(actual.totalScore()).isEqualTo(malformedResponse.totalScore());
        assertThat(actual.passed()).isEqualTo(malformedResponse.passed());
        assertContextualReport(actual.overallFeedback(), "꼬리질문", "답변", "꼬리질문 피드백");
        // 최초 호출 + 재요청 1회 = 총 2회. 바깥쪽 @Retryable이 전체를 다시 실행했다면 4회 이상이었을 것이다.
        verify(llmClient, times(2)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("리포트 재요청이 null을 반환해도 최초 점수와 실제 면접 리포트를 보존한다")
    void evaluateFinalAnswers_invalidCareerReportRetryReturnsNull_preservesScore() {
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "null 재요청 피드백")),
                18,
                false,
                "일반 문장형 종합 피드백");
        when(llmClient.evaluateFinalAnswers(any()))
                .thenReturn(malformedResponse)
                .thenReturn(null);
        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(
                        5, "null 응답 뒤에도 보존할 질문", "null 응답 뒤에도 보존할 답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = sut.evaluateFinalAnswers(request);

        assertThat(actual.evaluations()).isEqualTo(malformedResponse.evaluations());
        assertThat(actual.totalScore()).isEqualTo(18);
        assertThat(actual.passed()).isFalse();
        assertContextualReport(
                actual.overallFeedback(),
                "null 응답 뒤에도 보존할 질문",
                "null 응답 뒤에도 보존할 답변",
                "null 재요청 피드백");
        verify(llmClient, times(2)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("리포트 재요청의 런타임 통신 실패도 최초 점수와 실제 면접 리포트로 흡수한다")
    void evaluateFinalAnswers_invalidCareerReportRetryThrowsRuntimeException_preservesScore() {
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 17, "통신 실패 전 확정 피드백")),
                17,
                false,
                "일반 문장형 종합 피드백");
        when(llmClient.evaluateFinalAnswers(any()))
                .thenReturn(malformedResponse)
                .thenThrow(new IllegalStateException("일시적인 공급자 통신 실패"));
        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(
                        5, "통신 실패 뒤에도 보존할 질문", "통신 실패 뒤에도 보존할 답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = sut.evaluateFinalAnswers(request);

        assertThat(actual.evaluations()).isEqualTo(malformedResponse.evaluations());
        assertThat(actual.totalScore()).isEqualTo(17);
        assertThat(actual.passed()).isFalse();
        assertContextualReport(
                actual.overallFeedback(),
                "통신 실패 뒤에도 보존할 질문",
                "통신 실패 뒤에도 보존할 답변",
                "통신 실패 전 확정 피드백");
        verify(llmClient, times(2)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("서버 문맥 리포트 검증까지 실패해도 최소 4섹션 리포트와 최초 점수를 반환한다")
    void evaluateFinalAnswers_contextualReportValidationFails_returnsMinimalReportWithScore() {
        LlmClient localClient = Mockito.mock(LlmClient.class);
        LlmResponseValidator localValidator = Mockito.mock(LlmResponseValidator.class);
        LlmInvocationService localSut = new LlmInvocationService(localClient, localValidator);
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 15, "최초 확정 피드백")),
                15,
                false,
                "일반 문장형 종합 피드백");
        when(localClient.evaluateFinalAnswers(any())).thenReturn(malformedResponse);
        when(localValidator.isCareerReportValid(any())).thenReturn(false);
        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "최종 안전망 질문", "최종 안전망 답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = localSut.evaluateFinalAnswers(request);

        assertThat(actual.evaluations()).isEqualTo(malformedResponse.evaluations());
        assertThat(actual.totalScore()).isEqualTo(15);
        assertThat(actual.passed()).isFalse();
        assertThat(actual.overallFeedback())
                .startsWith(CareerReportValidator.MINIMAL_SAFE_REPORT.stripTrailing())
                .endsWith(CareerReportValidator.HYPOTHETICAL_DISCLAIMER)
                .doesNotContain(CareerReportValidator.FALLBACK_REPORT);
        verify(localClient, times(2)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("최종 리포트 형식 이탈 후 재요청이 성공하면 재요청 리포트를 쓰되 최초 점수는 그대로 보존된다(#167, failure-policy.md §2)")
    void evaluateFinalAnswers_invalidCareerReportRetrySucceeds_usesRetriedReportWithOriginalScore() {
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "꼬리질문 피드백")),
                18,
                false,
                "일반 문장형 종합 피드백");
        var validRetryResponse = new FinalEvaluationResponse(
                List.of(eval(5, 20, "재요청 피드백")),
                20,
                true,
                validCareerReport());
        when(llmClient.evaluateFinalAnswers(any()))
                .thenReturn(malformedResponse)
                .thenReturn(validRetryResponse);

        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = sut.evaluateFinalAnswers(request);

        // 재요청이 유효해도 점수는 재요청 응답이 아니라 최초 응답 값을 그대로 쓴다.
        assertThat(actual.evaluations()).isEqualTo(malformedResponse.evaluations());
        assertThat(actual.totalScore()).isEqualTo(malformedResponse.totalScore());
        assertThat(actual.passed()).isEqualTo(malformedResponse.passed());
        // 리포트는 재요청에서 받은 유효한 본문 + 서버가 덧붙인 가상 수치 고지를 쓴다.
        assertThat(actual.overallFeedback())
                .startsWith(validRetryResponse.overallFeedback().stripTrailing())
                .endsWith(CareerReportValidator.HYPOTHETICAL_DISCLAIMER)
                .doesNotContain(CareerReportValidator.FALLBACK_REPORT);
        verify(llmClient, times(2)).evaluateFinalAnswers(any());
    }

    @Test
    @DisplayName("최종 리포트가 유효하면 가상 수치 고지가 리포트 끝에 덧붙는다")
    void evaluateFinalAnswers_validCareerReport_appendsDisclaimer() {
        var validResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "꼬리질문 피드백")),
                18,
                false,
                validCareerReport());
        when(llmClient.evaluateFinalAnswers(any())).thenReturn(validResponse);

        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = sut.evaluateFinalAnswers(request);
        assertThat(actual.evaluations()).isEqualTo(validResponse.evaluations());
        assertThat(actual.totalScore()).isEqualTo(validResponse.totalScore());
        assertThat(actual.passed()).isEqualTo(validResponse.passed());
        assertThat(actual.overallFeedback())
                .startsWith(validResponse.overallFeedback().stripTrailing())
                .endsWith(CareerReportValidator.HYPOTHETICAL_DISCLAIMER);
        verify(llmClient, times(1)).evaluateFinalAnswers(any());
    }

    /** 리소스 프롬프트 전달 overload에도 재요청·컨텍스트 리포트·점수 보존 흐름을 검증한다. */
    @Test
    @DisplayName("최종 채점 프롬프트 전달 overload도 반복 형식 이탈 시 실제 면접 리포트와 최초 점수를 반환한다")
    void evaluateFinalAnswersWithPrompt_invalidCareerReportRetriesOnceThenFallsBack() {
        var malformedResponse = new FinalEvaluationResponse(
                List.of(eval(5, 18, "꼬리질문 피드백")),
                18,
                false,
                "일반 문장형 종합 피드백");
        var malformedRetryResponse = new FinalEvaluationResponse(
                List.of(eval(5, 20, "꼬리질문 피드백 - 재요청")),
                20,
                true,
                "재요청도 여전히 일반 문장형");
        when(llmClient.evaluateFinalAnswers(any(), any(LlmPrompt.class)))
                .thenReturn(malformedResponse)
                .thenReturn(malformedRetryResponse);

        var request = EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(5, "꼬리질문", "답변", "모범답변")),
                previousContexts(),
                "STRICT",
                "홍길동");

        FinalEvaluationResponse actual = sut.evaluateFinalAnswers(request, new LlmPrompt("system", "user"));

        assertThat(actual.totalScore()).isEqualTo(malformedResponse.totalScore());
        assertThat(actual.passed()).isEqualTo(malformedResponse.passed());
        assertContextualReport(actual.overallFeedback(), "꼬리질문", "답변", "꼬리질문 피드백");
        verify(llmClient, times(2)).evaluateFinalAnswers(any(), any(LlmPrompt.class));
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

    /** 반복 형식 이탈 뒤에도 고유 면접 문맥을 포함한 정상 4섹션 리포트가 반환되는지 확인한다. */
    private void assertContextualReport(String report, String... expectedContextFragments) {
        assertThat(report)
                .contains("🎯 총평", "✨ 이런 점이 매우 훌륭했어요")
                .contains("🚀 합격을 확정 짓는 2%", "💡 Next Step")
                .contains("❌ AS-IS", "⭕ TO-BE")
                .contains(expectedContextFragments)
                .endsWith(CareerReportValidator.HYPOTHETICAL_DISCLAIMER)
                .doesNotContain(CareerReportValidator.FALLBACK_REPORT);
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
