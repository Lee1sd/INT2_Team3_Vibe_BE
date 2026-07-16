package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "real")
public class ClaudeLlmClient implements LlmClient {

    private final RestClient restClient;
    private final ClaudeJsonExtractor jsonExtractor;
    private final String model;
    private final int maxTokens;

    public ClaudeLlmClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${llm.model}") String model,
            @Value("${llm.anthropic.api-key:}") String apiKey,
            @Value("${llm.anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${llm.anthropic.version:2023-06-01}") String anthropicVersion,
            @Value("${llm.anthropic.max-tokens:2048}") int maxTokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("llm.anthropic.api-key must be configured for real LLM mode");
        }
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", anthropicVersion)
                .build();
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
                    .body(String.class);
            return jsonExtractor.parseContentJson(responseBody, responseType);
        } catch (LlmSchemaValidationException e) {
            throw e;
        } catch (RestClientException e) {
            throw new LlmSchemaValidationException("Claude API request failed", e);
        }
    }

    private LlmPrompt questionPromptFallback(QuestionGenerationRequest request) {
        return new LlmPrompt(
                "You are an interview question generation engine. Return only valid JSON.",
                """
                Generate exactly three interview questions and expected answers.
                Use turns 1, 2, and 3.
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

    private LlmPrompt initialEvaluationPrompt(EvaluationRequest request) {
        return new LlmPrompt(
                "You are an interview answer scoring engine. Return only valid JSON.",
                """
                Score turns 1-3. Use the five rubric fields for each item.
                Response schema:
                {"evaluations":[{"turn":1,"score":0,"technicalAccuracy":0,"coreCoverage":0,"reasoning":0,"specificity":0,"tradeOffsAndExceptions":0,"feedback":"..."}],"totalScore":0,"weakestQuestionId":1,"passed":false}
                personaTone: %s
                userName: %s
                pairs:
                %s
                """.formatted(request.personaTone(), request.userName(), formatPairs(request.questionAnswerPairs())));
    }

    private LlmPrompt finalEvaluationPrompt(EvaluationRequest request) {
        return new LlmPrompt(
                "You are an interview answer scoring engine. Return only valid JSON.",
                """
                Score only turn 4. Do not rescore turns 1-3; use previous evaluations only for overall feedback context.
                Response schema:
                {"evaluations":[{"turn":4,"score":0,"technicalAccuracy":0,"coreCoverage":0,"reasoning":0,"specificity":0,"tradeOffsAndExceptions":0,"feedback":"..."}],"totalScore":0,"passed":false,"overallFeedback":"..."}
                personaTone: %s
                userName: %s
                turn4:
                %s
                previousEvaluations:
                %s
                """.formatted(
                        request.personaTone(),
                        request.userName(),
                        formatPairs(request.questionAnswerPairs()),
                        formatPreviousEvaluations(request.previousEvaluations())));
    }

    private String formatPairs(List<QuestionAnswerPair> pairs) {
        StringBuilder builder = new StringBuilder();
        for (QuestionAnswerPair pair : pairs) {
            builder.append("- turn ").append(pair.turn())
                    .append("\n  question: ").append(pair.questionText())
                    .append("\n  userAnswer: ").append(pair.userAnswer())
                    .append("\n  expectedAnswer: ").append(pair.expectedAnswer())
                    .append('\n');
        }
        return builder.toString();
    }

    private String formatPreviousEvaluations(List<PreviousEvaluationContext> contexts) {
        StringBuilder builder = new StringBuilder();
        for (PreviousEvaluationContext context : contexts) {
            builder.append("- turn ").append(context.turn())
                    .append("\n  question: ").append(context.questionText())
                    .append("\n  userAnswer: ").append(context.userAnswer())
                    .append("\n  score: ").append(context.score())
                    .append("\n  feedback: ").append(context.feedback())
                    .append('\n');
        }
        return builder.toString();
    }
}
