package com.careerdungeon.domain.interview.service;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

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
 * UTF-8 채점 템플릿에 최초·최종 평가 컨텍스트를 주입한다.
 *
 * <p>질문 생성 프롬프트와 동일하게 템플릿은 {@code src/main/resources/prompts/**}에서
 * 관리하고, 이 컴포넌트는 동적 값 치환만 담당한다.
 */
@Component
public class ScoringPromptProvider {

    private static final String SYSTEM_TEMPLATE_PATH = "prompts/scoring/system.txt";
    private static final String INITIAL_TEMPLATE_PATH = "prompts/scoring/initial-user.txt";
    private static final String FINAL_TEMPLATE_PATH = "prompts/scoring/final-user.txt";
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{([a-zA-Z0-9]+)}}");

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /** 최초 turn 1~3 채점용 system/user 프롬프트를 조립한다. */
    public ScoringPrompt initialPrompt(EvaluationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> values = Map.of(
                "personaTone", requiredText(request.personaTone(), "personaTone"),
                "userName", requiredText(request.userName(), "userName"),
                "questionAnswerPairs", formatPairs(request.questionAnswerPairs()));
        return new ScoringPrompt(
                loadTemplate(SYSTEM_TEMPLATE_PATH),
                renderTemplate(INITIAL_TEMPLATE_PATH, values));
    }

    /** turn 4 단독 채점과 최초 turn 1~3 종합 피드백용 프롬프트를 조립한다. */
    public ScoringPrompt finalPrompt(EvaluationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> values = Map.of(
                "personaTone", requiredText(request.personaTone(), "personaTone"),
                "userName", requiredText(request.userName(), "userName"),
                "turn4", formatPairs(request.questionAnswerPairs()),
                "previousEvaluations", formatPreviousEvaluations(request.previousEvaluations()));
        return new ScoringPrompt(
                loadTemplate(SYSTEM_TEMPLATE_PATH),
                renderTemplate(FINAL_TEMPLATE_PATH, values));
    }

    /** 템플릿 토큰을 한 번만 치환해 사용자 입력 안의 토큰 모양 문자열은 재해석하지 않는다. */
    private String renderTemplate(String path, Map<String, String> values) {
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

    /** 질문·답변·저장된 모범답안을 turn별 입력 문자열로 변환한다. */
    private String formatPairs(List<QuestionAnswerPair> pairs) {
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

    /** 최초 확정 점수와 피드백을 최종 종합 피드백용 읽기 전용 문자열로 변환한다. */
    private String formatPreviousEvaluations(List<PreviousEvaluationContext> contexts) {
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
    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    /** 클래스패스의 UTF-8 템플릿을 최초 한 번만 읽어 캐시한다. */
    private String loadTemplate(String path) {
        return templateCache.computeIfAbsent(path, this::readTemplate);
    }

    /** 템플릿 파일 누락이나 읽기 실패를 설정 오류로 전환한다. */
    private String readTemplate(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Scoring prompt template not found: " + path, e);
        }
    }
}
