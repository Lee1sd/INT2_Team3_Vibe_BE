package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.dto.InterviewAnswerEvaluationResponse;
import com.careerdungeon.domain.interview.dto.InterviewAnswerItemRequest;
import com.careerdungeon.domain.interview.dto.InterviewAnswerSubmitRequest;
import com.careerdungeon.domain.interview.dto.InterviewAnswerSubmitResponse;
import com.careerdungeon.domain.interview.dto.InterviewCreateRequest;
import com.careerdungeon.domain.interview.dto.InterviewCreateResponse;
import com.careerdungeon.domain.interview.dto.InterviewNextTurnResponse;
import com.careerdungeon.domain.interview.dto.InterviewQuestionResponse;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.InterviewSessionStatus;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.service.AnswerSubmissionService;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import com.careerdungeon.global.exception.BusinessException;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class InterviewService {

    private static final String FOLLOW_UP_MESSAGE_UNIQUE_CONSTRAINT = "UQ_MESSAGES_SESSION_ROLE_TURN";
    private static final String ANSWER_MESSAGE_UNIQUE_CONSTRAINT = "UQ_MESSAGES_SESSION_ROLE_TURN";

    private static final Set<String> MVP_ALLOWED_KEYWORDS = Set.of("DB", "보안");
    private static final Set<Integer> INITIAL_ANSWER_TURNS = Set.of(1, 2, 3);
    private static final Set<Integer> FINAL_ANSWER_TURNS = Set.of(4);

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final PersonaConfigRepository personaConfigRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final MessageRepository messageRepository;
    private final QuestionRepository questionRepository;
    private final UserUnlockStatusRepository userUnlockStatusRepository;
    private final QuestionGenerationPromptProvider promptProvider;
    private final LlmInvocationService llmInvocationService;
    private final AnswerSubmissionService answerSubmissionService;
    private final TransactionTemplate transactionTemplate;

    public InterviewService(
            UserRepository userRepository,
            ResumeRepository resumeRepository,
            PersonaConfigRepository personaConfigRepository,
            InterviewSessionRepository interviewSessionRepository,
            MessageRepository messageRepository,
            QuestionRepository questionRepository,
            UserUnlockStatusRepository userUnlockStatusRepository,
            QuestionGenerationPromptProvider promptProvider,
            LlmInvocationService llmInvocationService,
            AnswerSubmissionService answerSubmissionService,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.personaConfigRepository = personaConfigRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.userUnlockStatusRepository = userUnlockStatusRepository;
        this.promptProvider = promptProvider;
        this.llmInvocationService = llmInvocationService;
        this.answerSubmissionService = answerSubmissionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public InterviewCreateResponse createInterview(Long userId, InterviewCreateRequest request) {
        String keyword = validateKeyword(request.keyword());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        Resume resume = findOwnedResume(userId, request.resumeId());
        PersonaConfig personaConfig = personaConfigRepository.findById(request.interviewerId())
                .orElseThrow(() -> new BusinessException(
                        "PERSONA_NOT_FOUND",
                        "면접관 설정을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        validateInterviewerUnlocked(userId, personaConfig);

        InterviewSession session = interviewSessionRepository.save(
                new InterviewSession(user, resume, personaConfig, keyword));

        QuestionGenerationRequest llmRequest = new QuestionGenerationRequest(
                resume.getExtractedText(),
                keyword,
                personaConfig.getTone().name(),
                user.getName());
        QuestionGenerationPrompt prompt = promptProvider.prompt(llmRequest);
        QuestionGenerationResponse llmResponse = llmInvocationService.generateQuestions(
                llmRequest,
                toLlmPrompt(prompt));

        List<InterviewQuestionResponse> questions = llmResponse.questions().stream()
                .sorted(Comparator.comparingInt(GeneratedQuestion::turn))
                .map(generatedQuestion -> saveQuestion(session, generatedQuestion))
                .toList();

        return new InterviewCreateResponse(session.getId(), session.getStatus().name(), questions);
    }

    public InterviewAnswerSubmitResponse submitAnswers(
            Long userId,
            Long sessionId,
            InterviewAnswerSubmitRequest request) {
        validateSessionOwner(findSession(sessionId), userId);
        List<ResolvedAnswer> answers = resolveAnswers(sessionId, request.answers());
        if (hasAnswerTurns(answers, INITIAL_ANSWER_TURNS)) {
            return submitInitialAnswers(userId, sessionId, answers);
        }
        if (hasAnswerTurns(answers, FINAL_ANSWER_TURNS)) {
            return submitFinalAnswer(userId, sessionId, answers);
        }
        throw invalidAnswerTurnSet();
    }

    private InterviewAnswerSubmitResponse submitInitialAnswers(
            Long userId,
            Long sessionId,
            List<ResolvedAnswer> answers) {
        validateAnswerTurns(answers, INITIAL_ANSWER_TURNS);
        InitialSubmissionContext context = transactionTemplate.execute(status ->
                prepareInitialSubmission(userId, sessionId, answers));
        transactionTemplate.executeWithoutResult(status -> claimInitialSubmission(userId, sessionId));

        try {
            InitialEvaluationResponse rawInitial = llmInvocationService.evaluateInitialAnswers(
                    EvaluationRequest.initial(context.pairs(), context.tone(), context.userName()));
            InitialJudgmentEvaluation scoredInitial = answerSubmissionService.scoreInitial(rawInitial);
            FollowUpGenerationResponse followUp = generateFollowUp(context, scoredInitial);

            return transactionTemplate.execute(status ->
                    persistInitialSubmission(userId, sessionId, scoredInitial, followUp));
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> releaseInitialSubmission(userId, sessionId));
            throw e;
        }
    }

    private InterviewAnswerSubmitResponse submitFinalAnswer(
            Long userId,
            Long sessionId,
            List<ResolvedAnswer> answers) {
        validateAnswerTurns(answers, FINAL_ANSWER_TURNS);
        FinalSubmissionContext context = transactionTemplate.execute(status ->
                prepareFinalSubmission(userId, sessionId, answers.get(0)));
        transactionTemplate.executeWithoutResult(status -> claimFinalSubmission(userId, sessionId));

        try {
            FinalEvaluationResponse rawFinal = llmInvocationService.evaluateFinalAnswers(
                    EvaluationRequest.finalEvaluation(
                            List.of(context.followUpPair()),
                            context.previousEvaluations(),
                            context.tone(),
                            context.userName()));
            FinalJudgmentEvaluation scoredFinal = answerSubmissionService.scoreFinal(
                    context.storedInitial(),
                    rawFinal);

            return transactionTemplate.execute(status ->
                    persistFinalSubmission(userId, sessionId, scoredFinal));
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> releaseFinalSubmission(userId, sessionId));
            throw e;
        }
    }

    @Transactional
    public InterviewQuestionResponse generateFollowUpQuestion(Long userId, Long sessionId) {
        InterviewSession session = interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "INTERVIEW_SESSION_NOT_FOUND",
                        "면접 세션을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(
                    "INTERVIEW_SESSION_FORBIDDEN",
                    "본인의 면접 세션만 사용할 수 있습니다.",
                    HttpStatus.FORBIDDEN);
        }
        validateFollowUpGenerationStatus(session);
        if (messageRepository.existsBySession_IdAndRoleAndTurn(sessionId, MessageRole.QUESTION, 4)) {
            throw followUpAlreadyExists();
        }

        List<QuestionAnswerPair> pairs = IntStream.rangeClosed(1, 3)
                .mapToObj(turn -> findQuestionAnswerPair(sessionId, turn))
                .toList();
        String tone = session.getPersonaConfig().getTone().name();
        String userName = session.getUser().getName();
        InitialEvaluationResponse initialEvaluation = llmInvocationService.evaluateInitialAnswers(
                EvaluationRequest.initial(pairs, tone, userName));
        InitialJudgmentEvaluation scoredInitial = answerSubmissionService.scoreInitial(initialEvaluation);

        int weakestQuestionId = scoredInitial.weakestQuestionId();
        QuestionAnswerPair weakestPair = pairs.stream()
                .filter(pair -> pair.turn() == weakestQuestionId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "WEAKEST_QUESTION_NOT_FOUND",
                        "최저점 문항을 찾을 수 없습니다.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        String feedback = scoredInitial.evaluations().stream()
                .filter(evaluation -> evaluation.questionId() == weakestQuestionId)
                .map(QuestionScore::feedback)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "WEAKEST_QUESTION_FEEDBACK_NOT_FOUND",
                        "최저점 문항 피드백을 찾을 수 없습니다.",
                        HttpStatus.INTERNAL_SERVER_ERROR));

        QuestionGenerationPrompt prompt = promptProvider.followUpPrompt(
                tone,
                userName,
                weakestQuestionId,
                weakestPair.questionText(),
                weakestPair.userAnswer(),
                feedback);
        FollowUpGenerationResponse followUp = llmInvocationService.generateFollowUp(
                weakestQuestionId,
                weakestPair.questionText(),
                weakestPair.userAnswer(),
                feedback,
                toLlmPrompt(prompt));

        answerSubmissionService.persistInitialScores(session, scoredInitial);
        Message followUpMessage = saveFollowUpQuestion(session, followUp);
        questionRepository.save(new Question(followUpMessage, followUp.expectedAnswer()));
        session.awaitFollowup();
        return new InterviewQuestionResponse(4, followUp.followUpQuestion());
    }

    private InitialSubmissionContext prepareInitialSubmission(
            Long userId,
            Long sessionId,
            List<ResolvedAnswer> answers) {
        InterviewSession session = findSessionForUpdate(sessionId);
        validateSessionOwner(session, userId);
        if (answerSubmissionService.hasInitialScores(sessionId)) {
            throw answerAlreadySubmitted();
        }
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw invalidAnswerSubmissionStatus();
        }
        ensureAnswersAvailable(session, answers, INITIAL_ANSWER_TURNS);
        List<QuestionAnswerPair> pairs = INITIAL_ANSWER_TURNS.stream()
                .sorted()
                .map(turn -> findQuestionAnswerPair(sessionId, turn))
                .toList();
        return new InitialSubmissionContext(
                pairs,
                session.getPersonaConfig().getTone().name(),
                session.getUser().getName());
    }

    private void claimInitialSubmission(Long userId, Long sessionId) {
        InterviewSession session = findSessionForUpdate(sessionId);
        validateSessionOwner(session, userId);
        if (answerSubmissionService.hasInitialScores(sessionId)) {
            throw answerAlreadySubmitted();
        }
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw invalidAnswerSubmissionStatus();
        }
        session.startInitialScoring();
    }

    private void releaseInitialSubmission(Long userId, Long sessionId) {
        InterviewSession session = findSessionForUpdate(sessionId);
        validateSessionOwner(session, userId);
        if (session.getStatus() == InterviewSessionStatus.SCORING_INITIAL
                && !answerSubmissionService.hasInitialScores(sessionId)) {
            session.resetToInProgress();
        }
    }

    private InterviewAnswerSubmitResponse persistInitialSubmission(
            Long userId,
            Long sessionId,
            InitialJudgmentEvaluation scoredInitial,
            FollowUpGenerationResponse followUp) {
        InterviewSession session = findSessionForUpdate(sessionId);
        validateSessionOwner(session, userId);
        if (answerSubmissionService.hasInitialScores(sessionId)) {
            throw answerAlreadySubmitted();
        }
        if (session.getStatus() != InterviewSessionStatus.SCORING_INITIAL) {
            throw invalidAnswerSubmissionStatus();
        }
        answerSubmissionService.persistInitialScores(session, scoredInitial);
        Message followUpMessage = saveFollowUpQuestion(session, followUp);
        questionRepository.save(new Question(followUpMessage, followUp.expectedAnswer()));
        session.awaitFollowup();
        return initialResponse(scoredInitial, followUp);
    }

    private FinalSubmissionContext prepareFinalSubmission(
            Long userId,
            Long sessionId,
            ResolvedAnswer answer) {
        InterviewSession session = findSessionForUpdate(sessionId);
        validateSessionOwner(session, userId);
        if (answerSubmissionService.hasFinalResult(sessionId)) {
            throw answerAlreadySubmitted();
        }
        if (session.getStatus() != InterviewSessionStatus.AWAITING_FOLLOWUP) {
            throw invalidAnswerSubmissionStatus();
        }
        ensureAnswersAvailable(session, List.of(answer), FINAL_ANSWER_TURNS);
        InitialJudgmentEvaluation storedInitial = answerSubmissionService.loadStoredInitialEvaluation(sessionId);
        QuestionAnswerPair followUpPair = findQuestionAnswerPair(sessionId, 4);
        List<PreviousEvaluationContext> previousEvaluations = storedInitial.evaluations().stream()
                .sorted(Comparator.comparingInt(QuestionScore::questionId))
                .map(score -> previousEvaluation(sessionId, score))
                .toList();
        return new FinalSubmissionContext(
                storedInitial,
                followUpPair,
                previousEvaluations,
                session.getPersonaConfig().getTone().name(),
                session.getUser().getName());
    }

    private void claimFinalSubmission(Long userId, Long sessionId) {
        InterviewSession session = findSessionForUpdate(sessionId);
        validateSessionOwner(session, userId);
        if (answerSubmissionService.hasFinalResult(sessionId)) {
            throw answerAlreadySubmitted();
        }
        if (session.getStatus() != InterviewSessionStatus.AWAITING_FOLLOWUP) {
            throw invalidAnswerSubmissionStatus();
        }
        session.startFinalScoring();
    }

    private void releaseFinalSubmission(Long userId, Long sessionId) {
        InterviewSession session = findSessionForUpdate(sessionId);
        validateSessionOwner(session, userId);
        if (session.getStatus() == InterviewSessionStatus.SCORING_FINAL
                && !answerSubmissionService.hasFinalResult(sessionId)) {
            session.resetToAwaitingFollowup();
        }
    }

    private InterviewAnswerSubmitResponse persistFinalSubmission(
            Long userId,
            Long sessionId,
            FinalJudgmentEvaluation scoredFinal) {
        InterviewSession session = findSessionForUpdate(sessionId);
        validateSessionOwner(session, userId);
        if (answerSubmissionService.hasFinalResult(sessionId)) {
            throw answerAlreadySubmitted();
        }
        if (session.getStatus() != InterviewSessionStatus.SCORING_FINAL) {
            throw invalidAnswerSubmissionStatus();
        }
        answerSubmissionService.persistFinalResult(session, scoredFinal);
        session.complete();
        return finalResponse(scoredFinal);
    }

    private FollowUpGenerationResponse generateFollowUp(
            InitialSubmissionContext context,
            InitialJudgmentEvaluation scoredInitial) {
        int weakestQuestionId = scoredInitial.weakestQuestionId();
        QuestionAnswerPair weakestPair = context.pairs().stream()
                .filter(pair -> pair.turn() == weakestQuestionId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "WEAKEST_QUESTION_NOT_FOUND",
                        "최저점 문항을 찾을 수 없습니다.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        String feedback = scoredInitial.evaluations().stream()
                .filter(evaluation -> evaluation.questionId() == weakestQuestionId)
                .map(QuestionScore::feedback)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "WEAKEST_QUESTION_FEEDBACK_NOT_FOUND",
                        "최저점 문항 피드백을 찾을 수 없습니다.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        QuestionGenerationPrompt prompt = promptProvider.followUpPrompt(
                context.tone(),
                context.userName(),
                weakestQuestionId,
                weakestPair.questionText(),
                weakestPair.userAnswer(),
                feedback);
        return llmInvocationService.generateFollowUp(
                weakestQuestionId,
                weakestPair.questionText(),
                weakestPair.userAnswer(),
                feedback,
                toLlmPrompt(prompt));
    }

    private void validateFollowUpGenerationStatus(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "INTERVIEW_SESSION_INVALID_STATUS",
                    "꼬리질문을 생성할 수 없는 면접 세션 상태입니다.",
                    HttpStatus.CONFLICT);
        }
    }

    private List<ResolvedAnswer> resolveAnswers(Long sessionId, List<InterviewAnswerItemRequest> answers) {
        if (answers == null) {
            return List.of();
        }
        return answers.stream()
                .map(answer -> resolveAnswer(sessionId, answer))
                .toList();
    }

    private ResolvedAnswer resolveAnswer(Long sessionId, InterviewAnswerItemRequest answer) {
        if (answer == null || answer.questionId() == null) {
            throw invalidAnswerTurnSet();
        }
        int turn = answer.questionId();
        if (!messageRepository.existsBySession_IdAndRoleAndTurn(sessionId, MessageRole.QUESTION, turn)) {
            throw new BusinessException(
                    "INTERVIEW_QUESTION_NOT_FOUND",
                    "질문을 찾을 수 없습니다.",
                    HttpStatus.BAD_REQUEST);
        }
        return new ResolvedAnswer(turn, answer.answerText());
    }

    private void validateAnswerTurns(List<ResolvedAnswer> answers, Set<Integer> expectedTurns) {
        if (!hasAnswerTurns(answers, expectedTurns)) {
            throw invalidAnswerTurnSet();
        }
    }

    private boolean hasAnswerTurns(List<ResolvedAnswer> answers, Set<Integer> expectedTurns) {
        if (answers == null
                || answers.size() != expectedTurns.size()
                || answers.stream().anyMatch(answer -> answer == null)) {
            return false;
        }
        Set<Integer> actualTurns = answers.stream()
                .map(ResolvedAnswer::turn)
                .collect(Collectors.toSet());
        return actualTurns.equals(expectedTurns);
    }

    private BusinessException invalidAnswerTurnSet() {
        Set<Set<Integer>> expectedTurns = Set.of(INITIAL_ANSWER_TURNS, FINAL_ANSWER_TURNS);
        return new BusinessException(
                "INTERVIEW_ANSWER_TURNS_INVALID",
                "답변 문항 구성은 turn " + expectedTurns + " 이어야 합니다.",
                HttpStatus.BAD_REQUEST);
    }

    private void ensureAnswersAvailable(
            InterviewSession session,
            List<ResolvedAnswer> answers,
            Set<Integer> expectedTurns) {
        Map<Integer, ResolvedAnswer> byTurn = answers.stream()
                .collect(Collectors.toMap(
                        ResolvedAnswer::turn,
                        answer -> answer));
        expectedTurns.stream()
                .sorted()
                .filter(turn -> messageRepository.findBySession_IdAndRoleAndTurn(
                        session.getId(),
                        MessageRole.ANSWER,
                        turn).isEmpty())
                .forEach(turn -> saveAnswer(session, byTurn.get(turn)));
    }

    private void saveAnswer(InterviewSession session, ResolvedAnswer answer) {
        try {
            messageRepository.saveAndFlush(new Message(
                    session,
                    MessageRole.ANSWER,
                    answer.answerText(),
                    answer.turn()));
        } catch (DataIntegrityViolationException e) {
            if (isMessageUniqueConstraintViolation(e, ANSWER_MESSAGE_UNIQUE_CONSTRAINT)) {
                return;
            }
            throw e;
        }
    }

    private PreviousEvaluationContext previousEvaluation(Long sessionId, QuestionScore score) {
        Message questionMessage = findMessage(sessionId, MessageRole.QUESTION, score.questionId());
        Message answerMessage = findMessage(sessionId, MessageRole.ANSWER, score.questionId());
        return new PreviousEvaluationContext(
                score.questionId(),
                questionMessage.getContent(),
                answerMessage.getContent(),
                score.score(),
                score.feedback());
    }

    private InterviewAnswerSubmitResponse initialResponse(
            InitialJudgmentEvaluation evaluation,
            FollowUpGenerationResponse followUp) {
        Integer weakestQuestionId = evaluation.weakestQuestionId();
        return new InterviewAnswerSubmitResponse(
                evaluation.evaluations().stream()
                        .sorted(Comparator.comparingInt(QuestionScore::questionId))
                        .map(this::answerEvaluationResponse)
                        .toList(),
                evaluation.totalScore(),
                weakestQuestionId,
                evaluation.passed(),
                null,
                new InterviewNextTurnResponse(
                        "FOLLOW_UP",
                        weakestQuestionId,
                        followUp.followUpQuestion()));
    }

    private InterviewAnswerSubmitResponse finalResponse(FinalJudgmentEvaluation evaluation) {
        return new InterviewAnswerSubmitResponse(
                evaluation.evaluations().stream()
                        .sorted(Comparator.comparingInt(QuestionScore::questionId))
                        .map(this::answerEvaluationResponse)
                        .toList(),
                evaluation.totalScore(),
                null,
                evaluation.passed(),
                evaluation.overallFeedback(),
                null);
    }

    private InterviewAnswerEvaluationResponse answerEvaluationResponse(QuestionScore score) {
        return new InterviewAnswerEvaluationResponse(
                score.questionId(),
                score.score(),
                score.feedback());
    }

    private Message saveFollowUpQuestion(InterviewSession session, FollowUpGenerationResponse followUp) {
        try {
            return messageRepository.saveAndFlush(new Message(
                    session,
                    MessageRole.QUESTION,
                    followUp.followUpQuestion(),
                    4));
        } catch (DataIntegrityViolationException e) {
            if (isFollowUpUniqueConstraintViolation(e)) {
                throw followUpAlreadyExists();
            }
            throw e;
        }
    }

    private boolean isFollowUpUniqueConstraintViolation(DataIntegrityViolationException e) {
        return isMessageUniqueConstraintViolation(e, FOLLOW_UP_MESSAGE_UNIQUE_CONSTRAINT);
    }

    private boolean isMessageUniqueConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private BusinessException followUpAlreadyExists() {
        return new BusinessException(
                "FOLLOW_UP_ALREADY_EXISTS",
                "이미 생성된 꼬리질문이 있습니다.",
                HttpStatus.CONFLICT);
    }

    private BusinessException answerAlreadySubmitted() {
        return new BusinessException(
                "INTERVIEW_ANSWER_ALREADY_SUBMITTED",
                "이미 제출된 답변이 있습니다.",
                HttpStatus.CONFLICT);
    }

    private BusinessException invalidAnswerSubmissionStatus() {
        return new BusinessException(
                "INTERVIEW_SESSION_INVALID_STATUS",
                "답변을 제출할 수 없는 면접 세션 상태입니다.",
                HttpStatus.CONFLICT);
    }

    private LlmPrompt toLlmPrompt(QuestionGenerationPrompt prompt) {
        return new LlmPrompt(prompt.systemPrompt(), prompt.userPrompt());
    }

    private String validateKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw invalidKeyword();
        }
        String normalizedKeyword = keyword.strip();
        if (!MVP_ALLOWED_KEYWORDS.contains(normalizedKeyword)) {
            throw invalidKeyword();
        }
        return normalizedKeyword;
    }

    private void validateInterviewerUnlocked(Long userId, PersonaConfig personaConfig) {
        UserUnlockStatus unlockStatus = userUnlockStatusRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_UNLOCK_STATUS_NOT_FOUND",
                        "사용자 진행도 상태를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        if (personaConfig.getLevel() > unlockStatus.getUnlockedLevel()) {
            throw new BusinessException(
                    "INTERVIEWER_LOCKED",
                    "아직 해금되지 않은 면접관입니다.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private BusinessException invalidKeyword() {
        return new BusinessException(
                "INTERVIEW_KEYWORD_INVALID",
                "면접 키워드는 MVP 허용 목록(DB, 보안) 중 하나여야 합니다.",
                HttpStatus.BAD_REQUEST);
    }

    private Resume findOwnedResume(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new BusinessException(
                        "RESUME_NOT_FOUND",
                        "이력서를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
        if (!resume.getUserId().equals(userId)) {
            throw new BusinessException(
                    "RESUME_FORBIDDEN",
                    "본인의 이력서만 사용할 수 있습니다.",
                    HttpStatus.FORBIDDEN);
        }
        if (resume.getType() != ResumeType.RESUME) {
            throw new BusinessException(
                    "RESUME_TYPE_INVALID",
                    "면접 세션에는 RESUME 타입 이력서만 사용할 수 있습니다.",
                    HttpStatus.BAD_REQUEST);
        }
        if (resume.getParseStatus() != ParseStatus.DONE) {
            throw new BusinessException(
                    "RESUME_PARSE_NOT_DONE",
                    "이력서 파싱이 완료된 뒤 면접 세션을 생성할 수 있습니다.",
                    HttpStatus.BAD_REQUEST);
        }
        if (resume.getExtractedText() == null || resume.getExtractedText().isBlank()) {
            throw new BusinessException(
                    "RESUME_TEXT_EMPTY",
                    "이력서 추출 텍스트가 없습니다.",
                    HttpStatus.BAD_REQUEST);
        }
        return resume;
    }

    private InterviewSession findSession(Long sessionId) {
        return interviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "INTERVIEW_SESSION_NOT_FOUND",
                        "면접 세션을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
    }

    private InterviewSession findSessionForUpdate(Long sessionId) {
        return interviewSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(
                        "INTERVIEW_SESSION_NOT_FOUND",
                        "면접 세션을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));
    }

    private void validateSessionOwner(InterviewSession session, Long userId) {
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(
                    "INTERVIEW_SESSION_FORBIDDEN",
                    "본인의 면접 세션만 사용할 수 있습니다.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private QuestionAnswerPair findQuestionAnswerPair(Long sessionId, int turn) {
        Message questionMessage = findMessage(sessionId, MessageRole.QUESTION, turn);
        Message answerMessage = findMessage(sessionId, MessageRole.ANSWER, turn);
        Question question = questionRepository.findById(questionMessage.getId())
                .orElseThrow(() -> new BusinessException(
                        "QUESTION_NOT_FOUND",
                        "질문 모범답안을 찾을 수 없습니다.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        return new QuestionAnswerPair(
                turn,
                questionMessage.getContent(),
                answerMessage.getContent(),
                question.getExpectedAnswer());
    }

    private Message findMessage(Long sessionId, MessageRole role, int turn) {
        return messageRepository.findBySession_IdAndRoleAndTurn(sessionId, role, turn)
                .orElseThrow(() -> new BusinessException(
                        "INTERVIEW_MESSAGE_NOT_FOUND",
                        "면접 메시지를 찾을 수 없습니다.",
                        HttpStatus.BAD_REQUEST));
    }

    private InterviewQuestionResponse saveQuestion(InterviewSession session, GeneratedQuestion generatedQuestion) {
        Message message = messageRepository.save(new Message(
                session,
                MessageRole.QUESTION,
                generatedQuestion.questionText(),
                generatedQuestion.turn()));
        questionRepository.save(new Question(message, generatedQuestion.expectedAnswer()));
        return new InterviewQuestionResponse(generatedQuestion.turn(), generatedQuestion.questionText());
    }

    private record InitialSubmissionContext(
            List<QuestionAnswerPair> pairs,
            String tone,
            String userName) {
    }

    private record FinalSubmissionContext(
            InitialJudgmentEvaluation storedInitial,
            QuestionAnswerPair followUpPair,
            List<PreviousEvaluationContext> previousEvaluations,
            String tone,
            String userName) {
    }

    private record ResolvedAnswer(
            int turn,
            String answerText) {
    }
}
