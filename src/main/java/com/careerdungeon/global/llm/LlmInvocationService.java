package com.careerdungeon.global.llm;

import com.careerdungeon.global.exception.LlmPermanentFailureException;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.exception.LlmProviderConfigException;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.careerdungeon.global.llm.validation.CareerReportFallbackFactory;
import com.careerdungeon.global.llm.validation.CareerReportValidator;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import com.careerdungeon.global.llm.validation.PreviousEvaluationContextValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LlmInvocationService.class);
    private static final Set<Integer> FINAL_REQUEST_TURNS = Set.of(5);

    private final LlmClient llmClient;
    private final LlmResponseValidator validator;

    public LlmInvocationService(LlmClient llmClient, LlmResponseValidator validator) {
        this.llmClient = llmClient;
        this.validator = validator;
    }

    /**
     * 질문 4개 + 모범답변 생성. 세션당 1회 호출 (llm-cost-policy.md §4).
     * 스키마 이탈 시 최대 1회 재요청.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            notRecoverable = LlmProviderConfigException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request) {
        QuestionGenerationResponse response = llmClient.generateQuestions(request);
        validator.validate(response);
        return response;
    }

    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            notRecoverable = LlmProviderConfigException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request, LlmPrompt prompt) {
        QuestionGenerationResponse response = llmClient.generateQuestions(request, prompt);
        validator.validate(response);
        return response;
    }

    @Recover
    public QuestionGenerationResponse recoverGenerateQuestions(
            LlmSchemaValidationException e, QuestionGenerationRequest request) {
        throw new LlmPermanentFailureException(
                "질문 생성에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    @Recover
    public QuestionGenerationResponse recoverGenerateQuestions(
            LlmSchemaValidationException e, QuestionGenerationRequest request, LlmPrompt prompt) {
        throw new LlmPermanentFailureException(
                "질문 생성에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    /**
     * IS-002 최초 4문항 채점. 세션당 1회 호출.
     * 스키마 이탈 시 최대 1회 재요청.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            notRecoverable = LlmProviderConfigException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request) {
        InitialEvaluationResponse response = llmClient.evaluateInitialAnswers(request);
        validator.validateInitialEvaluation(response);
        return response;
    }

    /** 리소스에서 조립한 최초 채점 프롬프트를 사용하되 기존 검증·재시도 정책을 동일하게 적용한다. */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            notRecoverable = LlmProviderConfigException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request, LlmPrompt prompt) {
        InitialEvaluationResponse response = llmClient.evaluateInitialAnswers(request, prompt);
        validator.validateInitialEvaluation(response);
        return response;
    }

    @Recover
    public InitialEvaluationResponse recoverEvaluateInitialAnswers(
            LlmSchemaValidationException e, EvaluationRequest request) {
        throw new LlmPermanentFailureException(
                "채점에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    @Recover
    public InitialEvaluationResponse recoverEvaluateInitialAnswers(
            LlmSchemaValidationException e, EvaluationRequest request, LlmPrompt prompt) {
        throw new LlmPermanentFailureException(
                "채점에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            notRecoverable = LlmProviderConfigException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public FollowUpGenerationResponse generateFollowUp(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback) {
        FollowUpGenerationResponse response = llmClient.generateFollowUp(
                weakestQuestionId,
                questionText,
                userAnswer,
                feedback);
        validator.validateFollowUpGeneration(response);
        return response;
    }

    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            notRecoverable = LlmProviderConfigException.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public FollowUpGenerationResponse generateFollowUp(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback,
            LlmPrompt prompt) {
        FollowUpGenerationResponse response = llmClient.generateFollowUp(
                weakestQuestionId,
                questionText,
                userAnswer,
                feedback,
                prompt);
        validator.validateFollowUpGeneration(response);
        return response;
    }

    @Recover
    public FollowUpGenerationResponse recoverGenerateFollowUp(
            LlmSchemaValidationException e,
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback) {
        throw new LlmPermanentFailureException(
                "꼬리질문 생성에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    @Recover
    public FollowUpGenerationResponse recoverGenerateFollowUp(
            LlmSchemaValidationException e,
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback,
            LlmPrompt prompt) {
        throw new LlmPermanentFailureException(
                "꼬리질문 생성에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    /**
     * IS-002b 꼬리질문 최종 채점. 세션당 1회 호출 (1차 배치 채점 이후).
     * 최초 4문항은 서버 확정 점수를 재사용하고, 이 호출에는 turn 5의 질문·답변·모범답안만
     * 채점 대상으로 전달한다. 최초 1~4의 질문·답변·확정 점수·피드백은 종합 피드백을 위한
     * 읽기 전용 컨텍스트로만 전달한다(이슈 #60).
     * 요청 자체의 turn 구성이 잘못되면(호출자 계약 위반) 재시도 없이 즉시
     * {@link LlmPermanentFailureException}을 던진다 — 같은 입력은 재시도해도 절대
     * 성공하지 않으므로 재시도로 낭비하지 않는다(코드래빗 지적). LLM 응답 스키마
     * 이탈만 최대 1회 재요청 대상이다.
     */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            notRecoverable = {
                    LlmProviderConfigException.class,
                    LlmPermanentFailureException.class
            },
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request) {
        validateFinalEvaluationRequest(request);
        FinalEvaluationResponse response = llmClient.evaluateFinalAnswers(request);
        validator.validateFinalEvaluation(response);
        return withSanitizedReport(response, request);
    }

    /** 리소스에서 조립한 최종 채점 프롬프트를 사용하되 기존 검증·재시도 정책을 동일하게 적용한다. */
    @Retryable(
            retryFor = LlmSchemaValidationException.class,
            notRecoverable = {
                    LlmProviderConfigException.class,
                    LlmPermanentFailureException.class
            },
            maxAttempts = 2,
            backoff = @Backoff(delay = 500)
    )
    public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request, LlmPrompt prompt) {
        validateFinalEvaluationRequest(request);
        FinalEvaluationResponse response = llmClient.evaluateFinalAnswers(request, prompt);
        validator.validateFinalEvaluation(response);
        return withSanitizedReport(response, request);
    }

    /**
     * 리포트 콘텐츠 계약을 검증해 통과하면 가상 수치 고지를 붙인다. 형식이 어긋나면
     * 추가 LLM 호출 없이 검증을 통과한 결정형 리포트로 교체해 시연 비용과 확률적 실패를
     * 동시에 제거한다. 점수 필드는 최초 응답에서 확정된 값을 그대로 보존한다.
     */
    private FinalEvaluationResponse withSanitizedReport(
            FinalEvaluationResponse response,
            EvaluationRequest request) {
        String original = response.overallFeedback();
        if (validator.isCareerReportValid(original)) {
            return withReportText(response, CareerReportValidator.appendHypotheticalDisclaimer(original));
        }

        log.warn("커리어 리포트 콘텐츠 검증 실패, 실제 문항 평가 기반 결정형 리포트로 대체합니다.");
        String fallbackReport = CareerReportFallbackFactory.create(request, response);
        return withReportText(response, fallbackReport);
    }

    /** 점수(evaluations/totalScore/passed)는 원본 응답 값을 유지한 채 리포트 텍스트만 교체한다. */
    private FinalEvaluationResponse withReportText(FinalEvaluationResponse response, String reportText) {
        return new FinalEvaluationResponse(
                response.evaluations(),
                response.totalScore(),
                response.passed(),
                reportText);
    }

    /** 최종 채점의 turn 5 단독 대상과 최초 turn 1~4 읽기 전용 컨텍스트 계약을 검증한다. */
    private void validateFinalEvaluationRequest(EvaluationRequest request) {
        if (request == null) {
            throw new LlmPermanentFailureException("IS-002b 최종 채점 요청은 필수입니다.");
        }
        List<QuestionAnswerPair> pairs = request.questionAnswerPairs();
        if (pairs == null || pairs.stream().anyMatch(java.util.Objects::isNull)) {
            throw new LlmPermanentFailureException("IS-002b 최종 채점 요청에 null 문항이 있습니다.");
        }
        Set<Integer> requestTurns = pairs.stream()
                .map(QuestionAnswerPair::turn)
                .collect(Collectors.toSet());
        // pairs.size()도 함께 확인해 turn 5 중복 혼입을 놓치지 않는다.
        if (pairs.size() != FINAL_REQUEST_TURNS.size() || !requestTurns.equals(FINAL_REQUEST_TURNS)) {
            throw new LlmPermanentFailureException(
                    "IS-002b 최종 채점 요청은 turn " + FINAL_REQUEST_TURNS
                            + " 전체가 정확히 " + FINAL_REQUEST_TURNS.size() + "개 있어야 합니다: "
                            + pairs.size() + "개, turn=" + requestTurns);
        }
        QuestionAnswerPair followUp = pairs.get(0);
        if (isBlank(followUp.questionText()) || isBlank(followUp.userAnswer())
                || isBlank(followUp.expectedAnswer())) {
            throw new LlmPermanentFailureException(
                    "IS-002b turn 5의 질문, 사용자 답변, 모범답변은 필수입니다.");
        }
        validatePreviousEvaluations(request.previousEvaluations());
    }

    @Recover
    public FinalEvaluationResponse recoverEvaluateFinalAnswers(
            LlmSchemaValidationException e, EvaluationRequest request) {
        throw new LlmPermanentFailureException(
                "채점에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    @Recover
    public FinalEvaluationResponse recoverEvaluateFinalAnswers(
            LlmSchemaValidationException e, EvaluationRequest request, LlmPrompt prompt) {
        throw new LlmPermanentFailureException(
                "채점에 2회 연속 실패했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    /** 내부 최종 채점 요청의 필수 문자열 누락을 검사한다. */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 종합 피드백 컨텍스트가 최초 turn 1~4의 서버 확정 평가인지 검증한다. */
    private void validatePreviousEvaluations(List<PreviousEvaluationContext> contexts) {
        try {
            PreviousEvaluationContextValidator.validate(contexts);
        } catch (IllegalArgumentException e) {
            throw new LlmPermanentFailureException("IS-002b " + e.getMessage());
        }
    }
}
