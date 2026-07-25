package com.careerdungeon.global.llm;

import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
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
     * 이력서·키워드·페르소나 톤을 기반으로 면접 질문 4개와 모범답변을 생성한다 (FR-03).
     * 세션당 1회 호출 — 질문 4개를 한 번에 생성한다 (llm-cost-policy.md §4).
     */
    QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request);

    default QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request, LlmPrompt prompt) {
        return generateQuestions(request);
    }

    /**
     * IS-002 최초 채점 — 사용자 답변 4개를 일괄 채점한다 (FR-04).
     * 반환된 score·totalScore는 원시값이며, clamp(0~20, 0~100)는 ③(최용성)의 책임이다.
     */
    InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request);

    /** 도메인에서 조립한 채점 프롬프트를 전달하는 확장 지점이다. Mock 구현은 기존 계약을 재사용한다. */
    default InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request, LlmPrompt prompt) {
        return evaluateInitialAnswers(request);
    }

    FollowUpGenerationResponse generateFollowUp(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback);

    default FollowUpGenerationResponse generateFollowUp(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback,
            LlmPrompt prompt) {
        return generateFollowUp(weakestQuestionId, questionText, userAnswer, feedback);
    }

    /**
     * IS-002b 꼬리질문 최종 채점 — 최초 4문항은 서버 확정 점수를 유지하고 turn 5만 채점한다.
     * 최초 1~4의 질문·답변·확정 점수·피드백은 종합 피드백 생성용 읽기 전용 컨텍스트이며,
     * LLM이 이를 재채점하거나 최종 점수를 변경해서는 안 된다.
     * {@code weakestQuestionId}는 계약상 존재하지 않는다(이슈 #6, ADR-008).
     */
    FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request);

    /** 도메인에서 조립한 최종 채점 프롬프트를 전달하는 확장 지점이다. Mock 구현은 기존 계약을 재사용한다. */
    default FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request, LlmPrompt prompt) {
        return evaluateFinalAnswers(request);
    }
}
