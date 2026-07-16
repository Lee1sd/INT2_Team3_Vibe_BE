package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    static String stripMarkdownCodeFence(String raw) {
        if (raw == null) {
            throw new LlmSchemaValidationException("Claude response content must not be null");
        }
        String text = raw.strip();
        if (!text.startsWith("```")) {
            return text;
        }

        int firstLineEnd = text.indexOf('\n');
        int closingFenceStart = text.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFenceStart <= firstLineEnd) {
            return text;
        }
        return text.substring(firstLineEnd + 1, closingFenceStart).strip();
    }
}
