package com.careerdungeon.domain.interview.service;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.prompt.ScoringPromptTemplate;
import org.springframework.stereotype.Component;

/**
 * UTF-8 채점 템플릿에 최초·최종 평가 컨텍스트를 주입한다.
 *
 * <p>질문 생성 프롬프트와 동일하게 템플릿은 {@code src/main/resources/prompts/**}에서
 * 관리하고, 이 컴포넌트는 동적 값 치환만 담당한다.
 */
@Component
public class ScoringPromptProvider {

    /** 최초 turn 1~4 채점용 system/user 프롬프트를 조립한다. */
    public ScoringPrompt initialPrompt(EvaluationRequest request) {
        return toScoringPrompt(ScoringPromptTemplate.initialPrompt(request));
    }

    /** turn 5 단독 채점과 최초 turn 1~4 종합 피드백용 프롬프트를 조립한다. */
    public ScoringPrompt finalPrompt(EvaluationRequest request) {
        return toScoringPrompt(ScoringPromptTemplate.finalPrompt(request));
    }

    /** 공통 LLM 프롬프트 DTO를 기존 면접 서비스 값 객체로 변환한다. */
    private ScoringPrompt toScoringPrompt(LlmPrompt prompt) {
        return new ScoringPrompt(prompt.systemPrompt(), prompt.userPrompt());
    }
}
