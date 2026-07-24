package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeJsonExtractorTest {

    private final ClaudeJsonExtractor sut = new ClaudeJsonExtractor(new ObjectMapper());

    @Test
    void parseContentJson_stripsJsonMarkdownCodeFence() {
        String responseBody = """
                {
                  "content": [
                    {
                      "type": "text",
                      "text": "```json\\n{\\\"followUpQuestion\\\":\\\"추가 질문\\\",\\\"expectedAnswer\\\":\\\"모범 답안\\\"}\\n```"
                    }
                  ]
                }
                """;

        FollowUpGenerationResponse result = sut.parseContentJson(responseBody, FollowUpGenerationResponse.class);

        assertThat(result.followUpQuestion()).isEqualTo("추가 질문");
        assertThat(result.expectedAnswer()).isEqualTo("모범 답안");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("overallFeedback 등 JSON 문자열 값 안에 중첩된 ```가 있어도 진짜 닫는 펜스만 벗겨낸다")
    void parseContentJson_ignoresNestedCodeFenceInsideJsonStringValue() {
        String responseBody = """
                {
                  "content": [
                    {
                      "type": "text",
                      "text": "```json\\n{\\\"followUpQuestion\\\":\\\"예시 코드입니다.\\\\n```\\\\nSELECT 1;\\\\n```\\\",\\\"expectedAnswer\\\":\\\"모범 답안\\\"}\\n```"
                    }
                  ]
                }
                """;

        FollowUpGenerationResponse result = sut.parseContentJson(responseBody, FollowUpGenerationResponse.class);

        assertThat(result.followUpQuestion()).contains("SELECT 1;");
        assertThat(result.expectedAnswer()).isEqualTo("모범 답안");
    }
}
