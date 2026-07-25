package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

final class ClaudeJsonExtractor {

    private final ObjectMapper objectMapper;

    ClaudeJsonExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    <T> T parseContentJson(String responseBody, Class<T> responseType) {
        String text = extractText(responseBody);
        String json = stripMarkdownCodeFence(text);
        try {
            return objectMapper.readValue(json, responseType);
        } catch (Exception e) {
            throw new LlmSchemaValidationException("Claude response content is not valid JSON", e);
        }
    }

    String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (!content.isArray()) {
                throw new LlmSchemaValidationException("Claude response content must be an array");
            }
            for (JsonNode block : content) {
                if ("text".equals(block.path("type").asText()) && !block.path("text").asText().isBlank()) {
                    return block.path("text").asText();
                }
            }
            throw new LlmSchemaValidationException("Claude response does not contain text content");
        } catch (LlmSchemaValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmSchemaValidationException("Claude response body is not valid JSON", e);
        }
    }

    /**
     * 응답 전체를 감싼 마크다운 코드펜스만 벗겨낸다. overallFeedback 본문 안에 예시 코드
     * 블록처럼 중첩된 ``` 가 있을 수 있으므로, {@code lastIndexOf}로 아무 ``` 나 닫는
     * 펜스로 오인하지 않도록 "그 줄 전체가 ``` 뿐인 마지막 줄"만 닫는 펜스로 인정한다.
     */
    static String stripMarkdownCodeFence(String raw) {
        if (raw == null) {
            throw new LlmSchemaValidationException("Claude response content must not be null");
        }
        String text = raw.strip();
        if (!text.startsWith("```")) {
            return text;
        }

        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        if (lines.size() < 2) {
            return text;
        }
        lines.remove(0);

        int closingLineIndex = lines.size() - 1;
        while (closingLineIndex >= 0 && lines.get(closingLineIndex).isBlank()) {
            closingLineIndex--;
        }
        if (closingLineIndex < 0 || !"```".equals(lines.get(closingLineIndex).strip())) {
            return text;
        }
        return String.join("\n", lines.subList(0, closingLineIndex)).strip();
    }
}
