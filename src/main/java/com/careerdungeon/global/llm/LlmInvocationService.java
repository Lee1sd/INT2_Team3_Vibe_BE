package com.careerdungeon.global.llm;

import com.careerdungeon.global.exception.LlmPermanentFailureException;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.EvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * LLM 호출 + 응답 검증 + 재시도를 담당하는 서비스.
 *
 * <p>도메인 서비스({@code InterviewService} 등)는 {@link LlmClient}를 직접 호출하지 않고
 * 이 서비스를 통해 호출한다. 재시도(최대 2회), 폴백, 검증이 여기서 처리된다.
 *
 * <p>재시도 정책 (NFR-05, llm-cost-policy.md §4):
 * <ul>
 *   <li>최초 호출 + 재시도 2회 = 총 3회</li>
 *   <li>3회 모두 실패 시 {@link LlmPermanentFailureException} throw → GlobalExceptionHandler가 처리</li>
 *   <li>재시도 간 500ms 대기 (LLM 일시적 응답 불안정 대응)</li>
 * </ul>
 *
 * <p>점수 clamp는 이 서비스에서 하지 않는다 — ③(judgment 도메인)의 책임이다.
 */
@Service
public class LlmInvocationService {

    private final LlmClient llmClient;
    private final LlmResponseValidator validator;

    public LlmInvocationService(LlmClient llmClient, LlmResponseValidator validator) {
        this.llmClient = llmClient;
        this.validator = validator;
    }

    /**
     * 질문 3개 + 모범답변 생성. 세션당 1회 호출 (llm-cost-policy.md §4).
     * 스키마 이탈 시 최대 2회 재요청.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500)
    )
    public QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request) {
        QuestionGenerationResponse response = llmClient.generateQuestions(request);
        validator.validate(response);
        return response;
    }

    @Recover
    public QuestionGenerationResponse recoverGenerateQuestions(
            LlmSchemaValidationException e, QuestionGenerationRequest request) {
        throw new LlmPermanentFailureException(
                "질문 생성에 3회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    /**
     * 답변 일괄 채점. 세션당 최대 2회 호출 (1차 배치 + 꼬리질문 재채점).
     * 스키마 이탈 시 최대 2회 재요청.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500)
    )
    public EvaluationResponse evaluateAnswers(EvaluationRequest request) {
        EvaluationResponse response = llmClient.evaluateAnswers(request);
        Set<Integer> retainedTurns = request.retainedTurns();
        if (retainedTurns != null) {
            // IS-002b: 꼬리질문 최종 채점 — expectedTurns = retainedTurns ∪ {followUpTurn}
            int followUpTurn = request.questionAnswerPairs().get(0).turn();
            Set<Integer> expectedTurns = new HashSet<>(retainedTurns);
            expectedTurns.add(followUpTurn);
            validator.validateFinalEvaluation(response, followUpTurn, Set.copyOf(expectedTurns));
        } else {
            // IS-002: 최초 3문항 채점
            validator.validateInitialEvaluation(response);
        }
        return response;
    }

    @Recover
    public EvaluationResponse recoverEvaluateAnswers(
            LlmSchemaValidationException e, EvaluationRequest request) {
        throw new LlmPermanentFailureException(
                "채점에 3회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }
}
