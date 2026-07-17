package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.dto.InterviewCreateRequest;
import com.careerdungeon.domain.interview.dto.InterviewCreateResponse;
import com.careerdungeon.domain.interview.dto.InterviewQuestionResponse;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.InterviewSessionStatus;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.judgment.llm.LlmEvaluationResponseAdapter;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.service.JudgmentScoringService;
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
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class InterviewService {

    private static final String FOLLOW_UP_MESSAGE_UNIQUE_CONSTRAINT = "UQ_MESSAGES_SESSION_ROLE_TURN";

    private static final Set<String> MVP_ALLOWED_KEYWORDS = Set.of("DB", "보안");

    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final PersonaConfigRepository personaConfigRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final MessageRepository messageRepository;
    private final QuestionRepository questionRepository;
    private final UserUnlockStatusRepository userUnlockStatusRepository;
    private final QuestionGenerationPromptProvider promptProvider;
    private final LlmInvocationService llmInvocationService;
    private final LlmEvaluationResponseAdapter evaluationResponseAdapter;
    private final JudgmentScoringService judgmentScoringService;

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
            LlmEvaluationResponseAdapter evaluationResponseAdapter,
            JudgmentScoringService judgmentScoringService) {
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.personaConfigRepository = personaConfigRepository;
        this.interviewSessionRepository = interviewSessionRepository;
        this.messageRepository = messageRepository;
        this.questionRepository = questionRepository;
        this.userUnlockStatusRepository = userUnlockStatusRepository;
        this.promptProvider = promptProvider;
        this.llmInvocationService = llmInvocationService;
        this.evaluationResponseAdapter = evaluationResponseAdapter;
        this.judgmentScoringService = judgmentScoringService;
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
        InitialJudgmentEvaluation scoredInitial = judgmentScoringService.scoreInitial(
                evaluationResponseAdapter.toRawInitial(initialEvaluation));

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

        Message followUpMessage = saveFollowUpQuestion(session, followUp);
        questionRepository.save(new Question(followUpMessage, followUp.expectedAnswer()));
        session.awaitFollowup();
        return new InterviewQuestionResponse(followUpMessage.getId(), followUp.followUpQuestion());
    }

    private void validateFollowUpGenerationStatus(InterviewSession session) {
        if (session.getStatus() != InterviewSessionStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "INTERVIEW_SESSION_INVALID_STATUS",
                    "꼬리질문을 생성할 수 없는 면접 세션 상태입니다.",
                    HttpStatus.CONFLICT);
        }
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
        Throwable current = e;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(FOLLOW_UP_MESSAGE_UNIQUE_CONSTRAINT)) {
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
        return new InterviewQuestionResponse(message.getId(), generatedQuestion.questionText());
    }
}
