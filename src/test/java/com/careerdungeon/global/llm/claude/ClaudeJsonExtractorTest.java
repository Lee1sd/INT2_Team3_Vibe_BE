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
}
