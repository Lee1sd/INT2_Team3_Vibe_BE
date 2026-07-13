package com.careerdungeon.global.llm;

import com.careerdungeon.global.exception.LlmPermanentFailureException;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

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

    private static final Set<Integer> FINAL_REQUEST_TURNS = Set.of(1, 2, 3, 4);

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
     * IS-002 최초 3문항 채점. 세션당 1회 호출.
     * 스키마 이탈 시 최대 2회 재요청.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500)
    )
    public InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request) {
        InitialEvaluationResponse response = llmClient.evaluateInitialAnswers(request);
        validator.validateInitialEvaluation(response);
        return response;
    }

    @Recover
    public InitialEvaluationResponse recoverEvaluateInitialAnswers(
            LlmSchemaValidationException e, EvaluationRequest request) {
        throw new LlmPermanentFailureException(
                "채점에 3회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    /**
     * IS-002b 꼬리질문 최종 채점. 세션당 1회 호출 (1차 배치 채점 이후).
     * 최초 3문항과 꼬리질문을 합친 turn 1~4 전체를 질문·답변·모범답안과 함께 다시
     * 전달한다(ADR-010 — judgment의 EvaluationLlmClient가 요구하는 형태와 동일).
     * 스키마 이탈 시 최대 2회 재요청.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500)
    )
    public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request) {
        Set<Integer> requestTurns = request.questionAnswerPairs().stream()
                .map(QuestionAnswerPair::turn)
                .collect(Collectors.toSet());
        if (!requestTurns.equals(FINAL_REQUEST_TURNS)) {
            throw new LlmSchemaValidationException(
                    "IS-002b 최종 채점 요청은 turn " + FINAL_REQUEST_TURNS
                            + " 전체가 필요합니다: " + requestTurns);
        }
        FinalEvaluationResponse response = llmClient.evaluateFinalAnswers(request);
        validator.validateFinalEvaluation(response);
        return response;
    }

    @Recover
    public FinalEvaluationResponse recoverEvaluateFinalAnswers(
            LlmSchemaValidationException e, EvaluationRequest request) {
        throw new LlmPermanentFailureException(
                "채점에 3회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }
}
