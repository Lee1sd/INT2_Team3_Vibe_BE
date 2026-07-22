package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "llm.mode=real",
        "llm.anthropic.api-key=test-api-key"
})
@ActiveProfiles("test")
class ClaudeLlmClientRealModeContextTest {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ClaudeLlmClient claudeLlmClient;

    @Test
    void realModeCreatesClaudeLlmClientBean() {
        assertThat(llmClient).isSameAs(claudeLlmClient);
    }
}
