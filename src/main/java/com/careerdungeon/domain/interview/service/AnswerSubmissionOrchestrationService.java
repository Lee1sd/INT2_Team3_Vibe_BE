package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.interview.dto.InterviewQuestionResponse;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.InterviewSessionStatus;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.judgment.dto.AnswerSubmissionRequest;
import com.careerdungeon.domain.judgment.dto.AnswerSubmissionResponse;
import com.careerdungeon.domain.judgment.dto.FinalAnswerSubmissionResponse;
import com.careerdungeon.domain.judgment.dto.InitialAnswerSubmissionResponse;
import com.careerdungeon.domain.judgment.dto.NextTurnResponse;
import com.careerdungeon.domain.judgment.dto.SubmittedAnswerRequest;
import com.careerdungeon.domain.judgment.exception.AnswerSubmissionException;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.service.AnswerSubmissionService;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** IS-002 답변 준비, LLM 호출, judgment 결과 반영 순서를 interview 경계에서 조정한다. */
@Service
public class AnswerSubmissionOrchestrationService {

    private static final Set<Integer> INITIAL_TURNS = Set.of(1, 2, 3);
    private static final Set<Integer> FINAL_TURNS = Set.of(4);

    private final InterviewSessionRepository interviewSessionRepository;
    private final MessageRepository messageRepository;
    private final QuestionRepository questionRepository;
    private final LlmInvocationService llmInvocationService;
    private final InterviewService interviewService;
    private final AnswerSubmissionService answerSubmissionService;
    private final SubmissionConcurrencyGuard concurrencyGuard;
    private final TransactionTemplate transactionTemplate;

    /** 면접 저장소·LLM 호출기·judgment 소비 서비스와 짧은 트랜잭션 실행기를 주입받는다. */
    public AnswerSubmissionOrchestrationService(
            InterviewSessionRepository interviewSessionRepository,
            MessageRepository messageRepository,
            QuestionRepository questionRepository,
            LlmInvocationService llmInvocationService,
            InterviewService interviewService,
            AnswerSubmissionService answerSubmissionService,
            SubmissionConcurrencyGuard concurrencyGuard,
            PlatformTransactionManager transactionManager) {
        this.interviewSessionRepository = interviewSessionRepository;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.llmInvocationService = llmInvocationService;
        this.interviewService = interviewService;
        this.answerSubmissionService = answerSubmissionService;
        this.concurrencyGuard = concurrencyGuard;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 동일 세션 요청을 직렬화하고 준비·LLM 호출·결과 반영을 단계별로 수행한다.
     * LLM은 두 짧은 DB 트랜잭션 사이에서 호출해 커넥션과 행 잠금을 점유하지 않는다.
     */
    public AnswerSubmissionResponse submit(
            Long userId,
            Long sessionId,
            AnswerSubmissionRequest request) {
        validateRequest(userId, sessionId, request);
        return concurrencyGuard.execute(sessionId, () -> submitSerially(userId, sessionId, request));
    }

    /** 준비 스냅샷을 만든 뒤 세션 상태에 맞는 LLM 호출과 judgment 반영을 실행한다. */
    private AnswerSubmissionResponse submitSerially(
            Long userId,
            Long sessionId,
            AnswerSubmissionRequest request) {
        SubmissionPreparation preparation = inTransaction(
                () -> prepareSubmission(userId, sessionId, request.answers()));
        return switch (preparation.phase()) {
            case INITIAL -> evaluateAndCompleteInitial(preparation);
            case FINAL -> evaluateAndCompleteFinal(preparation);
        };
    }

    /** 세션을 짧게 잠가 상태·소유자·답변 문항을 검증하고 LLM 입력 스냅샷을 만든다. */
    private SubmissionPreparation prepareSubmission(
            Long userId,
            Long sessionId,
            List<SubmittedAnswerRequest> answers) {
        InterviewSession session = findLockedSession(sessionId);
        validateOwner(session, userId);
        return switch (session.getStatus()) {
            case IN_PROGRESS -> prepareInitial(session, answers);
            case AWAITING_FOLLOWUP -> prepareFinal(session, answers);
            case COMPLETED -> throw error(
                    "JUDGMENT_ALREADY_COMPLETED",
                    "이미 최종 판정이 완료된 면접 세션입니다.",
                    HttpStatus.CONFLICT);
        };
    }

    /** 최초 turn 1~3 질문·제출 답변·모범답안을 최초 채점 입력으로 준비한다. */
    private SubmissionPreparation prepareInitial(
            InterviewSession session,
            List<SubmittedAnswerRequest> answers) {
        if (answerSubmissionService.hasInitialScores(session.getId())) {
            throw error(
                    "JUDGMENT_INITIAL_ALREADY_SCORED",
                    "이미 최초 채점이 완료된 면접 세션입니다.",
                    HttpStatus.CONFLICT);
        }
        return SubmissionPreparation.initial(session, resolveAnswerPairs(session, answers, INITIAL_TURNS));
    }

    /** turn 4 입력과 judgment에 보존된 최초 확정 평가를 최종 LLM 입력으로 준비한다. */
    private SubmissionPreparation prepareFinal(
            InterviewSession session,
            List<SubmittedAnswerRequest> answers) {
        if (answerSubmissionService.hasFinalResult(session.getId())) {
            throw error(
                    "JUDGMENT_ALREADY_COMPLETED",
                    "이미 최종 판정이 완료된 면접 세션입니다.",
                    HttpStatus.CONFLICT);
        }
        InitialJudgmentEvaluation storedInitial = answerSubmissionService.loadStoredInitialEvaluation(
                session.getId());
        List<PreviousEvaluationContext> previousContexts = buildPreviousContexts(
                session.getId(),
                storedInitial.evaluations());
        return SubmissionPreparation.finalEvaluation(
                session,
                resolveAnswerPairs(session, answers, FINAL_TURNS),
                storedInitial,
                previousContexts);
    }

    /** interview에서 최초 LLM 평가와 꼬리질문 생성을 호출하고 원시 평가값은 judgment에 전달한다. */
    private InitialAnswerSubmissionResponse evaluateAndCompleteInitial(
            SubmissionPreparation preparation) {
        InitialEvaluationResponse rawEvaluation = llmInvocationService.evaluateInitialAnswers(
                EvaluationRequest.initial(
                        preparation.pairs(),
                        preparation.tone(),
                        preparation.userName()));
        InitialJudgmentEvaluation evaluation = answerSubmissionService.scoreInitial(rawEvaluation);
        FollowUpGenerationResponse followUp = interviewService.generateFollowUpQuestionContent(
                preparation.pairs(),
                preparation.tone(),
                preparation.userName(),
                evaluation);
        return inTransaction(() -> completeInitial(preparation, evaluation, followUp));
    }

    /** 최초 답변·확정 점수·꼬리질문·세션 상태를 하나의 반영 트랜잭션으로 저장한다. */
    private InitialAnswerSubmissionResponse completeInitial(
            SubmissionPreparation preparation,
            InitialJudgmentEvaluation evaluation,
            FollowUpGenerationResponse followUp) {
        InterviewSession session = findLockedSession(preparation.sessionId());
        validateOwner(session, preparation.userId());
        validateStatus(session, InterviewSessionStatus.IN_PROGRESS);
        if (answerSubmissionService.hasInitialScores(session.getId())) {
            throw error(
                    "JUDGMENT_INITIAL_ALREADY_SCORED",
                    "이미 최초 채점이 완료된 면접 세션입니다.",
                    HttpStatus.CONFLICT);
        }

        saveAnswers(session, preparation.pairs());
        answerSubmissionService.persistInitialScores(session, evaluation);
        InterviewQuestionResponse persistedFollowUp = interviewService.persistGeneratedFollowUpQuestion(
                preparation.userId(),
                preparation.sessionId(),
                followUp);
        long weakestQuestionId = evaluation.weakestQuestionId();
        return new InitialAnswerSubmissionResponse(
                answerSubmissionService.toInitialResponses(evaluation.evaluations()),
                evaluation.totalScore(),
                weakestQuestionId,
                evaluation.passed(),
                NextTurnResponse.followUp(weakestQuestionId, persistedFollowUp.question()));
    }

    /** interview에서 turn 4 LLM 평가를 호출하고 원시 평가값은 judgment에 전달해 합산한다. */
    private FinalAnswerSubmissionResponse evaluateAndCompleteFinal(
            SubmissionPreparation preparation) {
        FinalEvaluationResponse rawEvaluation = llmInvocationService.evaluateFinalAnswers(
                EvaluationRequest.finalEvaluation(
                        preparation.pairs(),
                        preparation.previousContexts(),
                        preparation.tone(),
                        preparation.userName()));
        FinalJudgmentEvaluation evaluation = answerSubmissionService.scoreFinal(
                preparation.storedInitial(),
                rawEvaluation);
        return inTransaction(() -> completeFinal(preparation, evaluation));
    }

    /** turn 4 답변과 judgment 판정·진행도, 세션 완료 전이를 하나의 트랜잭션으로 반영한다. */
    private FinalAnswerSubmissionResponse completeFinal(
            SubmissionPreparation preparation,
            FinalJudgmentEvaluation evaluation) {
        InterviewSession session = findLockedSession(preparation.sessionId());
        validateOwner(session, preparation.userId());
        validateStatus(session, InterviewSessionStatus.AWAITING_FOLLOWUP);
        if (answerSubmissionService.hasFinalResult(session.getId())) {
            throw error(
                    "JUDGMENT_ALREADY_COMPLETED",
                    "이미 최종 판정이 완료된 면접 세션입니다.",
                    HttpStatus.CONFLICT);
        }
        InitialJudgmentEvaluation currentInitial = answerSubmissionService.loadStoredInitialEvaluation(
                session.getId());
        if (!currentInitial.evaluations().equals(preparation.storedInitial().evaluations())) {
            throw error(
                    "JUDGMENT_INITIAL_SCORE_CHANGED",
                    "최종 채점 중 최초 확정 점수가 변경되었습니다. 다시 시도해 주세요.",
                    HttpStatus.CONFLICT);
        }

        saveAnswers(session, preparation.pairs());
        answerSubmissionService.persistFinalResult(session, evaluation);
        session.complete();
        return new FinalAnswerSubmissionResponse(
                answerSubmissionService.toFinalResponses(evaluation.evaluations()),
                evaluation.totalScore(),
                evaluation.passed(),
                evaluation.overallFeedback(),
                null);
    }

    /** 외부 questionId를 turn으로 해석하고 저장된 질문·모범답변과 LLM 입력으로 조립한다. */
    private List<QuestionAnswerPair> resolveAnswerPairs(
            InterviewSession session,
            List<SubmittedAnswerRequest> answers,
            Set<Integer> expectedTurns) {
        if (answers.size() != expectedTurns.size()) {
            throw invalidQuestionSet(expectedTurns);
        }

        Set<Integer> seenTurns = new HashSet<>();
        List<QuestionAnswerPair> resolved = new ArrayList<>();
        for (SubmittedAnswerRequest answer : answers) {
            if (answer == null || answer.questionId() == null
                    || answer.questionId() < Integer.MIN_VALUE
                    || answer.questionId() > Integer.MAX_VALUE
                    || answer.answerText() == null
                    || answer.answerText().isBlank()) {
                throw error(
                        "JUDGMENT_ANSWER_INVALID",
                        "questionId와 answerText는 필수입니다.",
                        HttpStatus.BAD_REQUEST);
            }
            int turn = answer.questionId().intValue();
            if (!expectedTurns.contains(turn) || !seenTurns.add(turn)) {
                throw invalidQuestionSet(expectedTurns);
            }
            Message questionMessage = findMessage(session.getId(), MessageRole.QUESTION, turn);
            Question question = questionRepository.findById(questionMessage.getId())
                    .orElseThrow(() -> error(
                            "JUDGMENT_EXPECTED_ANSWER_NOT_FOUND",
                            "질문 모범답안을 찾을 수 없습니다: questionId=" + turn,
                            HttpStatus.INTERNAL_SERVER_ERROR));
            resolved.add(new QuestionAnswerPair(
                    turn,
                    questionMessage.getContent(),
                    answer.answerText(),
                    question.getExpectedAnswer()));
        }
        if (!seenTurns.equals(expectedTurns)) {
            throw invalidQuestionSet(expectedTurns);
        }
        resolved.sort(Comparator.comparingInt(QuestionAnswerPair::turn));
        return List.copyOf(resolved);
    }

    /** 결과 적용 시 같은 turn 답변이 생겼는지 다시 확인한 뒤 사용자 답변을 저장한다. */
    private void saveAnswers(InterviewSession session, List<QuestionAnswerPair> pairs) {
        for (QuestionAnswerPair pair : pairs) {
            if (messageRepository.existsBySession_IdAndRoleAndTurn(
                    session.getId(), MessageRole.ANSWER, pair.turn())) {
                throw error(
                        "JUDGMENT_ANSWER_ALREADY_SUBMITTED",
                        "이미 제출된 답변이 있습니다: questionId=" + pair.turn(),
                        HttpStatus.CONFLICT);
            }
        }
        messageRepository.saveAll(pairs.stream()
                .map(pair -> new Message(session, MessageRole.ANSWER, pair.userAnswer(), pair.turn()))
                .toList());
    }

    /** 최초 질문·답변·확정 점수·피드백을 최종 LLM용 읽기 전용 컨텍스트로 조립한다. */
    private List<PreviousEvaluationContext> buildPreviousContexts(
            Long sessionId,
            List<QuestionScore> scores) {
        return scores.stream()
                .sorted(Comparator.comparingInt(QuestionScore::questionId))
                .map(score -> new PreviousEvaluationContext(
                        score.questionId(),
                        findMessage(sessionId, MessageRole.QUESTION, score.questionId()).getContent(),
                        findMessage(sessionId, MessageRole.ANSWER, score.questionId()).getContent(),
                        score.score(),
                        score.feedback()))
                .toList();
    }

    /** 답변 제출 진입점의 인증·세션·요청 필수값을 검증한다. */
    private void validateRequest(Long userId, Long sessionId, AnswerSubmissionRequest request) {
        if (userId == null) {
            throw error("JUDGMENT_UNAUTHENTICATED", "인증 정보가 필요합니다.", HttpStatus.UNAUTHORIZED);
        }
        if (sessionId == null) {
            throw error("JUDGMENT_SESSION_REQUIRED", "면접 세션 식별자가 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        if (request == null || request.answers() == null) {
            throw error("JUDGMENT_ANSWERS_REQUIRED", "answers는 필수입니다.", HttpStatus.BAD_REQUEST);
        }
    }

    /** 잠금 조회가 포함된 짧은 트랜잭션을 실행하고 null 결과를 계약 오류로 막는다. */
    private <T> T inTransaction(Supplier<T> work) {
        T result = transactionTemplate.execute(status -> work.get());
        if (result == null) {
            throw error(
                    "JUDGMENT_TRANSACTION_RESULT_MISSING",
                    "채점 트랜잭션 결과가 누락되었습니다.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return result;
    }

    /** 면접 세션 소유 경계에서 비관적 쓰기 잠금으로 세션을 조회한다. */
    private InterviewSession findLockedSession(Long sessionId) {
        return interviewSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> error(
                        "INTERVIEW_SESSION_NOT_FOUND",
                        "면접 세션을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
    }

    /** 결과 반영 전 준비 당시 세션 상태가 그대로인지 확인한다. */
    private void validateStatus(InterviewSession session, InterviewSessionStatus expectedStatus) {
        if (session.getStatus() != expectedStatus) {
            throw error(
                    "JUDGMENT_SESSION_STATE_CHANGED",
                    "채점 중 면접 세션 상태가 변경되었습니다. 다시 시도해 주세요.",
                    HttpStatus.CONFLICT);
        }
    }

    /** 세션 소유자가 아닌 사용자의 답변 제출을 차단한다. */
    private void validateOwner(InterviewSession session, Long userId) {
        if (!session.getUserId().equals(userId)) {
            throw error(
                    "INTERVIEW_SESSION_FORBIDDEN",
                    "본인의 면접 세션만 사용할 수 있습니다.",
                    HttpStatus.FORBIDDEN);
        }
    }

    /** 세션·역할·turn으로 메시지를 조회하고 누락을 명시적 계약 오류로 변환한다. */
    private Message findMessage(Long sessionId, MessageRole role, int turn) {
        return messageRepository.findBySession_IdAndRoleAndTurn(sessionId, role, turn)
                .orElseThrow(() -> error(
                        "JUDGMENT_MESSAGE_NOT_FOUND",
                        "면접 메시지를 찾을 수 없습니다: role=" + role + ", questionId=" + turn,
                        HttpStatus.BAD_REQUEST));
    }

    /** 상태별 필수 questionId 집합 위반을 400 예외로 만든다. */
    private AnswerSubmissionException invalidQuestionSet(Set<Integer> expectedTurns) {
        return error(
                "JUDGMENT_QUESTION_SET_INVALID",
                "답변 questionId 구성은 " + expectedTurns + "여야 합니다.",
                HttpStatus.BAD_REQUEST);
    }

    /** 공통 응답 계약으로 변환될 답변 제출 비즈니스 예외를 생성한다. */
    private AnswerSubmissionException error(String code, String message, HttpStatus status) {
        return new AnswerSubmissionException(code, message, status);
    }

    /** 답변 제출의 최초·최종 LLM 호출 단계를 구분한다. */
    private enum SubmissionPhase {
        INITIAL,
        FINAL
    }

    /** 준비 트랜잭션에서 확정한 LLM 입력과 결과 적용 재검증 정보를 보존한다. */
    private record SubmissionPreparation(
            SubmissionPhase phase,
            Long sessionId,
            Long userId,
            String tone,
            String userName,
            List<QuestionAnswerPair> pairs,
            InitialJudgmentEvaluation storedInitial,
            List<PreviousEvaluationContext> previousContexts
    ) {
        /** 준비 목록을 불변 복사해 트랜잭션 밖에서 안전하게 사용한다. */
        private SubmissionPreparation {
            pairs = List.copyOf(pairs);
            previousContexts = List.copyOf(previousContexts);
        }

        /** 최초 제출용 LLM 입력 스냅샷을 생성한다. */
        private static SubmissionPreparation initial(
                InterviewSession session,
                List<QuestionAnswerPair> pairs) {
            return new SubmissionPreparation(
                    SubmissionPhase.INITIAL,
                    session.getId(),
                    session.getUserId(),
                    session.getPersonaConfig().getTone().name(),
                    session.getUser().getName(),
                    pairs,
                    null,
                    List.of());
        }

        /** 최종 제출용 LLM 입력 스냅샷을 생성한다. */
        private static SubmissionPreparation finalEvaluation(
                InterviewSession session,
                List<QuestionAnswerPair> pairs,
                InitialJudgmentEvaluation storedInitial,
                List<PreviousEvaluationContext> previousContexts) {
            return new SubmissionPreparation(
                    SubmissionPhase.FINAL,
                    session.getId(),
                    session.getUserId(),
                    session.getPersonaConfig().getTone().name(),
                    session.getUser().getName(),
                    pairs,
                    storedInitial,
                    previousContexts);
        }
    }
}
