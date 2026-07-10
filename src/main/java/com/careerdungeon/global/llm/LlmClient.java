package com.careerdungeon.global.llm;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.EvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;

/**
 * LLM 호출 추상화 인터페이스. 도메인 코드는 이 인터페이스만 참조하며,
 * 벤더 SDK(Anthropic 등)는 구현체에서만 사용한다 (NFR-09).
 *
 * 호출 실패(JSON 스키마 검증 오류) 시 구현체는 {@link com.careerdungeon.global.llm.exception.LlmSchemaValidationException}을
 * 던진다. 호출 지점에서 최대 2회 재요청 후 실패 처리한다 (NFR-05, failure-policy.md §2).
 */
public interface LlmClient {

    /**
     * 이력서·키워드·페르소나 톤을 기반으로 면접 질문 3개와 모범답변을 생성한다 (FR-03).
     * 세션당 1회 호출 — 질문 3개를 한 번에 생성한다 (llm-cost-policy.md §4).
     */
    QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request);

    /**
     * 사용자 답변을 일괄 채점하고 평가 결과를 반환한다 (FR-04).
     * 반환된 score·totalScore는 원시값이며, clamp(0~25, 0~100)는 ③(최용성)의 책임이다.
     */
    EvaluationResponse evaluateAnswers(EvaluationRequest request);
}
