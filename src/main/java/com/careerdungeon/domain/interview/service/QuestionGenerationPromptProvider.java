package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.persona.PersonaPromptProvider;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QuestionGenerationPromptProvider {

    private static final String USER_TEMPLATE_PATH = "prompts/question-generation/user.txt";
    private static final String FOLLOW_UP_TEMPLATE_PATH = "prompts/question-generation/follow-up-user.txt";
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{([a-zA-Z0-9]+)}}");

    private final PersonaPromptProvider personaPromptProvider;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public QuestionGenerationPromptProvider(PersonaPromptProvider personaPromptProvider) {
        this.personaPromptProvider = Objects.requireNonNull(personaPromptProvider, "personaPromptProvider must not be null");
    }

    public QuestionGenerationPrompt prompt(QuestionGenerationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        PersonaTone tone = parseTone(request.personaTone());
        String systemPrompt = personaPromptProvider.systemPrompt(tone, requiredText(request.userName(), "userName"));
        String userPrompt = renderUserPrompt(request);
        return new QuestionGenerationPrompt(systemPrompt, userPrompt);
    }

    public QuestionGenerationPrompt followUpPrompt(
            String personaTone,
            String userName,
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback) {
        PersonaTone tone = parseTone(personaTone);
        String systemPrompt = personaPromptProvider.systemPrompt(tone, requiredText(userName, "userName"));
        Map<String, String> values = Map.of(
                "weakestQuestionId", String.valueOf(weakestQuestionId),
                "questionText", requiredText(questionText, "questionText"),
                "userAnswer", requiredText(userAnswer, "userAnswer"),
                "feedback", requiredText(feedback, "feedback"));
        String userPrompt = renderTemplate(FOLLOW_UP_TEMPLATE_PATH, values);
        return new QuestionGenerationPrompt(systemPrompt, userPrompt);
    }

    private String renderUserPrompt(QuestionGenerationRequest request) {
        Map<String, String> values = Map.of(
                "resumeText", requiredText(request.resumeText(), "resumeText"),
                "keyword", requiredText(request.keyword(), "keyword"));
        return renderTemplate(USER_TEMPLATE_PATH, values);
    }

    private String renderTemplate(String path, Map<String, String> values) {
        String template = templateCache.computeIfAbsent(path, this::loadTemplate);
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = values.get(token);
            if (replacement == null) {
                throw new IllegalStateException("Unknown question generation prompt token: " + token);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private PersonaTone parseTone(String rawTone) {
        String tone = requiredText(rawTone, "personaTone").toUpperCase();
        try {
            return PersonaTone.valueOf(tone);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported personaTone: " + rawTone, e);
        }
    }

    private String requiredText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }

    private String loadTemplate(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Question generation prompt template not found: " + path, e);
        }
    }
}
