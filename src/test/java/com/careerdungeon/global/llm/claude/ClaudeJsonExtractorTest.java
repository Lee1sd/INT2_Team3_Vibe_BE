package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("followUpQuestion 안에 중첩된 ```가 있어도 바깥쪽 펜스만 벗겨내고 중첩 펜스는 그대로 보존한다")
    void parseContentJson_preservesNestedCodeFenceInFollowUpQuestion() {
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

        assertThat(result.followUpQuestion()).isEqualTo("예시 코드입니다.\n```\nSELECT 1;\n```");
        assertThat(result.expectedAnswer()).isEqualTo("모범 답안");
    }

    @Test
    @DisplayName("닫는 펜스 뒤에 후행 텍스트가 있으면 마지막 줄이 순수 ```가 아니므로 원문을 그대로 반환한다(리뷰 지적)")
    void stripMarkdownCodeFence_trailingTextAfterFence_returnsRawUnstripped() {
        String raw = "```json\n{\"a\":1}\n```\nNote: extra trailing text";

        String result = ClaudeJsonExtractor.stripMarkdownCodeFence(raw);

        assertThat(result).isEqualTo(raw);
    }

    @Test
    @DisplayName(
            "중첩된 ```만 있고 별도의 외부 닫는 펜스가 없으면 원본 텍스트를 그대로 반환한다"
                    + " (lastIndexOf 기반 구현이었다면 중첩 펜스를 닫는 펜스로 오인해 JSON을 중간에서 잘랐을 상황)")
    void stripMarkdownCodeFence_returnsRawTextUnchanged_whenOnlyNestedFenceExistsWithoutOuterClosingFence() {
        String raw = """
                ```json
                {"followUpQuestion":"예시 ```SELECT 1``` 코드입니다","expectedAnswer":"모범 답안"}""";

        String result = ClaudeJsonExtractor.stripMarkdownCodeFence(raw);

        assertThat(result).isEqualTo(raw.strip());
    }
}
