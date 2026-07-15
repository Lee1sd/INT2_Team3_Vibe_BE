package com.careerdungeon.global.llm;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;

/**
 * LLM 호출 추상화 인터페이스. 도메인 코드는 이 인터페이스만 참조하며,
 * 벤더 SDK(Anthropic 등)는 구현체에서만 사용한다 (NFR-09).
 *
 * 호출 실패(JSON 스키마 검증 오류) 시 구현체는 {@link com.careerdungeon.global.llm.exception.LlmSchemaValidationException}을
 * 던진다. 호출 지점에서 최대 1회 재요청 후 실패 처리한다 (NFR-05, failure-policy.md §2).
 */
public interface LlmClient {

    /**
     * 이력서·키워드·페르소나 톤을 기반으로 면접 질문 3개와 모범답변을 생성한다 (FR-03).
     * 세션당 1회 호출 — 질문 3개를 한 번에 생성한다 (llm-cost-policy.md §4).
     */
    QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request);

    /**
     * IS-002 최초 채점 — 사용자 답변 3개를 일괄 채점한다 (FR-04).
     * 반환된 score·totalScore는 원시값이며, clamp(0~25, 0~100)는 ③(최용성)의 책임이다.
     */
    InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request);

    FollowUpGenerationResponse generateFollowUp(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback);

    /**
     * IS-002b 꼬리질문 최종 채점 — 최초 3문항과 꼬리질문을 합친 turn 1~4 전체를 채점한다
     * (ADR-010).
     * {@code weakestQuestionId}는 계약상 존재하지 않는다(이슈 #6, ADR-008).
     */
    FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request);
}
