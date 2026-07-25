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

    @Test
    @DisplayName("닫는 펜스 뒤에 후행 텍스트가 있으면 마지막 줄이 순수 ```가 아니므로 원문을 그대로 반환한다(리뷰 지적)")
    void stripMarkdownCodeFence_trailingTextAfterFence_returnsRawUnstripped() {
        String raw = "```json\n{\"a\":1}\n```\nNote: extra trailing text";

        String result = ClaudeJsonExtractor.stripMarkdownCodeFence(raw);

        assertThat(result).isEqualTo(raw);
    }
}
