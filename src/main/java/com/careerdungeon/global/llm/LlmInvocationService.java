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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM 호출 + 응답 검증 + 재시도를 담당하는 서비스.
 *
 * <p>도메인 서비스({@code InterviewService} 등)는 {@link LlmClient}를 직접 호출하지 않고
 * 이 서비스를 통해 호출한다. 재시도(최대 1회), 폴백, 검증이 여기서 처리된다.
 *
 * <p>재시도 정책 (NFR-05, llm-cost-policy.md §4):
 * <ul>
 *   <li>최초 호출 + 재시도 1회 = 총 2회</li>
 *   <li>2회 모두 실패 시 {@link LlmPermanentFailureException} throw → GlobalExceptionHandler가 처리</li>
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
     * 스키마 이탈 시 최대 1회 재요청.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            maxAttempts = 2,
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
                "질문 생성에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    /**
     * IS-002 최초 3문항 채점. 세션당 1회 호출.
     * 스키마 이탈 시 최대 1회 재요청.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            maxAttempts = 2,
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
                "채점에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    /**
     * IS-002b 꼬리질문 최종 채점. 세션당 1회 호출 (1차 배치 채점 이후).
     * 최초 3문항과 꼬리질문을 합친 turn 1~4 전체를 질문·답변·모범답안과 함께 다시
     * 전달한다(ADR-010 — judgment의 EvaluationLlmClient가 요구하는 형태와 동일).
     * 요청 자체의 turn 구성이 잘못되면(호출자 계약 위반) 재시도 없이 즉시
     * {@link LlmPermanentFailureException}을 던진다 — 같은 입력은 재시도해도 절대
     * 성공하지 않으므로 재시도로 낭비하지 않는다(코드래빗 지적). LLM 응답 스키마
     * 이탈만 최대 1회 재요청 대상이다.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request) {
        List<QuestionAnswerPair> pairs = request.questionAnswerPairs();
        Set<Integer> requestTurns = pairs.stream()
                .map(QuestionAnswerPair::turn)
                .collect(Collectors.toSet());
        // pairs.size()도 함께 확인 — turn 집합만 보면 [1,2,3,4,4] 같은 중복 혼입을 놓친다.
        if (pairs.size() != FINAL_REQUEST_TURNS.size() || !requestTurns.equals(FINAL_REQUEST_TURNS)) {
            throw new LlmPermanentFailureException(
                    "IS-002b 최종 채점 요청은 turn " + FINAL_REQUEST_TURNS
                            + " 전체가 정확히 " + FINAL_REQUEST_TURNS.size() + "개 있어야 합니다: "
                            + pairs.size() + "개, turn=" + requestTurns);
        }
        FinalEvaluationResponse response = llmClient.evaluateFinalAnswers(request);
        validator.validateFinalEvaluation(response);
        return response;
    }

    @Recover
    public FinalEvaluationResponse recoverEvaluateFinalAnswers(
            LlmSchemaValidationException e, EvaluationRequest request) {
        throw new LlmPermanentFailureException(
                "채점에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    /**
     * 요청 turn 검증 실패({@link LlmPermanentFailureException})는 {@code retryFor}에 없어
     * 첫 시도에서 바로 재시도가 중단되지만, {@code @Recover} 메서드가 있는 클래스에서는
     * Spring Retry가 예외 타입에 맞는 recover를 반드시 찾으려 한다 — 매칭되는 메서드가
     * 없으면 원래 예외 대신 {@code ExhaustedRetryException}으로 감싸버린다. 그대로
     * 재던지기만 해서 원래 예외가 그대로 전파되도록 한다(코드래빗 지적 대응 중 발견).
     */
    @Recover
    public FinalEvaluationResponse recoverEvaluateFinalAnswersFromPermanentFailure(
            LlmPermanentFailureException e, EvaluationRequest request) {
        throw e;
    }
}
