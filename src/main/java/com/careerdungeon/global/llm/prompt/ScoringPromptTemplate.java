package com.careerdungeon.global.llm.prompt;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ScoringPromptTemplate() {
    }

    /** 최초 1~4번 답변 채점용 system/user 프롬프트를 조립한다. */
    public static LlmPrompt initialPrompt(EvaluationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> values = Map.of(
                "interviewData", formatInitialInterviewData(request));
        return new LlmPrompt(
                loadTemplate(SYSTEM_TEMPLATE_PATH),
                renderTemplate(INITIAL_TEMPLATE_PATH, values));
    }

    /** 5번 답변 채점과 전체 리포트 생성용 system/user 프롬프트를 조립한다. */
    public static LlmPrompt finalPrompt(EvaluationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Map<String, String> values = Map.of(
                "interviewData", formatFinalInterviewData(request));
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

    /** 최초 채점의 모든 동적 값을 하나의 신뢰하지 않는 JSON 데이터 블록으로 직렬화한다. */
    private static String formatInitialInterviewData(EvaluationRequest request) {
        Map<String, Object> data = baseContext(request);
        data.put("questionAnswerPairs", formatPairs(request.questionAnswerPairs()));
        return toBoundarySafeJson(data);
    }

    /** 최종 채점의 모든 동적 값을 하나의 신뢰하지 않는 JSON 데이터 블록으로 직렬화한다. */
    private static String formatFinalInterviewData(EvaluationRequest request) {
        Map<String, Object> data = baseContext(request);
        data.put("turn5", formatPairs(request.questionAnswerPairs()));
        data.put("previousEvaluations", formatPreviousEvaluations(request.previousEvaluations()));
        return toBoundarySafeJson(data);
    }

    /** 호칭·페르소나 값도 지시문이 아니라 데이터로만 전달되도록 공통 컨텍스트에 넣는다. */
    private static Map<String, Object> baseContext(EvaluationRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("personaTone", requiredText(request.personaTone(), "personaTone"));
        data.put("userName", requiredText(request.userName(), "userName"));
        return data;
    }

    /** 질문·답변·저장된 모범답안을 JSON 객체 목록으로 변환한다. */
    private static List<Map<String, Object>> formatPairs(List<QuestionAnswerPair> pairs) {
        if (pairs == null) {
            throw new IllegalArgumentException("questionAnswerPairs must not be null");
        }
        List<Map<String, Object>> formatted = new ArrayList<>();
        for (QuestionAnswerPair pair : pairs) {
            if (pair == null) {
                throw new IllegalArgumentException("questionAnswerPairs must not contain null");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("turn", pair.turn());
            item.put("question", requiredText(pair.questionText(), "questionText"));
            item.put("userAnswer", requiredText(pair.userAnswer(), "userAnswer"));
            item.put("expectedAnswer", requiredText(pair.expectedAnswer(), "expectedAnswer"));
            formatted.add(item);
        }
        return formatted;
    }

    /** 최초 확정 평가를 전체 리포트용 읽기 전용 JSON 객체 목록으로 변환한다. */
    private static List<Map<String, Object>> formatPreviousEvaluations(
            List<PreviousEvaluationContext> contexts) {
        if (contexts == null) {
            throw new IllegalArgumentException("previousEvaluations must not be null");
        }
        List<Map<String, Object>> formatted = new ArrayList<>();
        for (PreviousEvaluationContext context : contexts) {
            if (context == null) {
                throw new IllegalArgumentException("previousEvaluations must not contain null");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("turn", context.turn());
            item.put("question", requiredText(context.questionText(), "previousQuestionText"));
            item.put("userAnswer", requiredText(context.userAnswer(), "previousUserAnswer"));
            item.put("confirmedScore", context.score());
            item.put("confirmedFeedback", requiredText(context.feedback(), "previousFeedback"));
            formatted.add(item);
        }
        return formatted;
    }

    /**
     * 동적 입력을 JSON으로 직렬화하고 XML 경계 문자를 유니코드 escape한다.
     *
     * <p>답변 안에 {@code </interview-data>} 같은 문자열이 있어도 실제 태그를 닫지 못하므로,
     * 최초·최종 채점 모두 동일한 프롬프트 인젝션 신뢰 경계를 유지한다.
     */
    private static String toBoundarySafeJson(Map<String, Object> data) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(data)
                    .replace("&", "\\u0026")
                    .replace("<", "\\u003C")
                    .replace(">", "\\u003E");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Scoring prompt data could not be serialized", e);
        }
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
