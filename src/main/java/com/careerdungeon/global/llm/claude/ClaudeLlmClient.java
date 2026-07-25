package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmProviderConfigException;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.careerdungeon.global.llm.prompt.ScoringPromptTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class ClaudeLlmClient implements LlmClient {

    private final RestClient restClient;
    private final ClaudeJsonExtractor jsonExtractor;
    private final String model;
    private final int maxTokens;

    @Autowired
    public ClaudeLlmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${llm.model}") String model,
            @Value("${llm.anthropic.api-key:}") String apiKey,
            @Value("${llm.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${llm.anthropic.version:2023-06-01}") String anthropicVersion,
            @Value("${llm.anthropic.max-tokens:2048}") int maxTokens,
            @Value("${llm.anthropic.connect-timeout:5s}") Duration connectTimeout,
            @Value("${llm.anthropic.read-timeout:30s}") Duration readTimeout) {
        this(
                restClientBuilder,
                objectMapper,
                model,
                apiKey,
                baseUrl,
                anthropicVersion,
                maxTokens,
                requestFactory(connectTimeout, readTimeout));
    }

    ClaudeLlmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            String model,
            String apiKey,
            String baseUrl,
            String anthropicVersion,
            int maxTokens,
            ClientHttpRequestFactory requestFactory) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("llm.anthropic.api-key must be configured for real LLM mode");
        }
        RestClient.Builder builder = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", anthropicVersion);
        if (requestFactory != null) {
            builder.requestFactory(requestFactory);
        }
        this.restClient = builder.build();
        this.jsonExtractor = new ClaudeJsonExtractor(objectMapper);
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request) {
        return generateQuestions(request, questionPromptFallback(request));
    }

    @Override
    public QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request, LlmPrompt prompt) {
        return invoke(prompt, QuestionGenerationResponse.class);
    }

    @Override
    public InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request) {
        return invoke(initialEvaluationPrompt(request), InitialEvaluationResponse.class);
    }

    /** 외부에서 조립한 UTF-8 리소스 기반 최초 채점 프롬프트로 Claude를 호출한다. */
    @Override
    public InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request, LlmPrompt prompt) {
        return invoke(prompt, InitialEvaluationResponse.class);
    }

    @Override
    public FollowUpGenerationResponse generateFollowUp(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback) {
        return generateFollowUp(
                weakestQuestionId,
                questionText,
                userAnswer,
                feedback,
                followUpPromptFallback(weakestQuestionId, questionText, userAnswer, feedback));
    }

    @Override
    public FollowUpGenerationResponse generateFollowUp(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback,
            LlmPrompt prompt) {
        return invoke(prompt, FollowUpGenerationResponse.class);
    }

    @Override
    public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request) {
        return invoke(finalEvaluationPrompt(request), FinalEvaluationResponse.class);
    }

    /** 외부에서 조립한 UTF-8 리소스 기반 최종 채점 프롬프트로 Claude를 호출한다. */
    @Override
    public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request, LlmPrompt prompt) {
        return invoke(prompt, FinalEvaluationResponse.class);
    }

    private <T> T invoke(LlmPrompt prompt, Class<T> responseType) {
        if (prompt == null) {
            throw new LlmSchemaValidationException("LLM prompt must be provided");
        }
        try {
            String responseBody = restClient.post()
                    .uri("/v1/messages")
                    .body(Map.of(
                            "model", model,
                            "max_tokens", maxTokens,
                            "system", prompt.systemPrompt(),
                            "messages", List.of(Map.of(
                                    "role", "user",
                                    "content", prompt.userPrompt()))))
                    .retrieve()
                    .onStatus(this::isNonRetryableProviderStatus, (request, response) -> {
                        throw new LlmProviderConfigException(
                                "Claude API request is not retryable: HTTP "
                                        + response.getStatusCode().value(),
                                response.getStatusCode().value());
                    })
                    .onStatus(this::isRetryableProviderStatus, (request, response) -> {
                        throw new LlmSchemaValidationException(
                                "Claude API request failed with retryable status",
                                null,
                                response.getStatusCode().value());
                    })
                    .body(String.class);
            return jsonExtractor.parseContentJson(responseBody, responseType);
        } catch (LlmProviderConfigException e) {
            throw e;
        } catch (LlmSchemaValidationException e) {
            throw e;
        } catch (RestClientException e) {
            throw new LlmSchemaValidationException("Claude API request failed", e);
        }
    }

    private boolean isNonRetryableProviderStatus(HttpStatusCode statusCode) {
        return statusCode.is4xxClientError() && !isRetryableProviderStatus(statusCode);
    }

    private boolean isRetryableProviderStatus(HttpStatusCode statusCode) {
        return statusCode.value() == 408
                || statusCode.value() == 409
                || statusCode.value() == 429
                || statusCode.is5xxServerError();
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    private LlmPrompt questionPromptFallback(QuestionGenerationRequest request) {
        return new LlmPrompt(
                "You are an interview question generation engine. Return only valid JSON.",
                """
                Generate exactly four interview questions and expected answers.
                Use turns 1, 2, 3, and 4.
                Response schema: {"questions":[{"turn":1,"questionText":"...","expectedAnswer":"..."}]}
                keyword: %s
                personaTone: %s
                userName: %s
                resumeText:
                %s
                """.formatted(request.keyword(), request.personaTone(), request.userName(), request.resumeText()));
    }

    private LlmPrompt followUpPromptFallback(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback) {
        return new LlmPrompt(
                "You are an interview follow-up question generation engine. Return only valid JSON.",
                """
                Generate one follow-up question and expected answer for the weakest answer.
                Response schema: {"followUpQuestion":"...","expectedAnswer":"..."}
                weakestQuestionId: %d
                questionText: %s
                userAnswer: %s
                feedback: %s
                """.formatted(weakestQuestionId, questionText, userAnswer, feedback));
    }

    /** Provider를 거치지 않는 하위 호환 호출에도 운영 경로와 동일한 최초 채점 리소스 프롬프트를 사용한다. */
    private LlmPrompt initialEvaluationPrompt(EvaluationRequest request) {
        return ScoringPromptTemplate.initialPrompt(request);
    }

    /** 하위 호환 호출도 운영 경로와 동일한 최종 채점 리소스 프롬프트를 사용한다. */
    private LlmPrompt finalEvaluationPrompt(EvaluationRequest request) {
        return ScoringPromptTemplate.finalPrompt(request);
    }

}
