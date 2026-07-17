package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.interview.service.InterviewService;
import com.careerdungeon.global.llm.LlmInvocationService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerSubmissionBoundaryTest {

    /** judgment 답변 처리 서비스가 LLM 호출기나 interview 서비스를 다시 주입받지 못하게 한다. */
    @Test
    void judgmentServiceDoesNotOrchestrateLlmOrInterview() {
        assertThat(Arrays.stream(AnswerSubmissionService.class.getDeclaredFields())
                .map(Field::getType))
                .doesNotContain(LlmInvocationService.class, InterviewService.class);
    }
}
