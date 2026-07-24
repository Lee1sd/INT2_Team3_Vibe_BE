package com.careerdungeon.global.llm.prompt;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UTF-8 채점 리소스 템플릿을 읽고 평가 요청 값을 주입하는 단일 조립기다.
 *
 * <p>면접 서비스의 명시적 프롬프트 경로와 Claude 하위 호환 경로가 같은 리소스를 사용해
 * 출력 계약이 서로 달라지지 않도록 한다.
 */
public final class ScoringPromptTemplate {

    private static final String SYSTEM_TEMPLATE_PATH = "prompts/scoring/system.txt";
    private static final String INITIAL_TEMPLATE_PATH = "prompts/scoring/initial-user.txt";
    private static final String FINAL_TEMPLATE_PATH = "prompts/scoring/final-user.txt";
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{([a-zA-Z0-9]+)}}");
    private static final Map<String, String> TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private ScoringPromptTemplate() {
    }

    /** 최초 1~4번 답변 채점용 system/user 프롬프트를 조립한다. */
    public static LlmPrompt initialPrompt(EvaluationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> values = Map.of(
                "personaTone", requiredText(request.personaTone(), "personaTone"),
                "userName", requiredText(request.userName(), "userName"),
                "questionAnswerPairs", formatPairs(request.questionAnswerPairs()));
        return new LlmPrompt(
                loadTemplate(SYSTEM_TEMPLATE_PATH),
                renderTemplate(INITIAL_TEMPLATE_PATH, values));
    }

    /** 5번 답변 채점과 전체 리포트 생성용 system/user 프롬프트를 조립한다. */
    public static LlmPrompt finalPrompt(EvaluationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> values = Map.of(
                "personaTone", requiredText(request.personaTone(), "personaTone"),
                "userName", requiredText(request.userName(), "userName"),
                "turn5", formatPairs(request.questionAnswerPairs()),
                "previousEvaluations", formatPreviousEvaluations(request.previousEvaluations()));
        return new LlmPrompt(
                loadTemplate(SYSTEM_TEMPLATE_PATH),
                renderTemplate(FINAL_TEMPLATE_PATH, values));
    }

    /** 템플릿 토큰을 한 번만 치환해 사용자 입력 속 토큰 모양 문자열은 재해석하지 않는다. */
    private static String renderTemplate(String path, Map<String, String> values) {
        String template = loadTemplate(path);
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = values.get(token);
            if (replacement == null) {
                throw new IllegalStateException("Unknown scoring prompt token: " + token);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /** 질문·답변·저장된 모범답안을 문항별 입력 문자열로 변환한다. */
    private static String formatPairs(List<QuestionAnswerPair> pairs) {
        StringBuilder builder = new StringBuilder();
        for (QuestionAnswerPair pair : pairs) {
            if (pair == null) {
                throw new IllegalArgumentException("questionAnswerPairs must not contain null");
            }
            builder.append("- turn ").append(pair.turn())
                    .append("\n  question: ").append(requiredText(pair.questionText(), "questionText"))
                    .append("\n  userAnswer: ").append(requiredText(pair.userAnswer(), "userAnswer"))
                    .append("\n  expectedAnswer: ").append(requiredText(pair.expectedAnswer(), "expectedAnswer"))
                    .append('\n');
        }
        return builder.toString();
    }

    /** 최초 확정 평가를 전체 리포트용 읽기 전용 문자열로 변환한다. */
    private static String formatPreviousEvaluations(List<PreviousEvaluationContext> contexts) {
        StringBuilder builder = new StringBuilder();
        for (PreviousEvaluationContext context : contexts) {
            if (context == null) {
                throw new IllegalArgumentException("previousEvaluations must not contain null");
            }
            builder.append("- turn ").append(context.turn())
                    .append("\n  question: ").append(requiredText(context.questionText(), "previousQuestionText"))
                    .append("\n  userAnswer: ").append(requiredText(context.userAnswer(), "previousUserAnswer"))
                    .append("\n  confirmedScore: ").append(context.score())
                    .append("\n  confirmedFeedback: ").append(requiredText(context.feedback(), "previousFeedback"))
                    .append('\n');
        }
        return builder.toString();
    }

    /** 동적 입력의 필수 문자열을 공백 제거 후 반환한다. */
    private static String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    /** 클래스패스의 UTF-8 템플릿을 최초 한 번만 읽어 캐시한다. */
    private static String loadTemplate(String path) {
        return TEMPLATE_CACHE.computeIfAbsent(path, ScoringPromptTemplate::readTemplate);
    }

    /** 템플릿 누락이나 읽기 실패를 설정 오류로 전환한다. */
    private static String readTemplate(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Scoring prompt template not found: " + path, e);
        }
    }
}
