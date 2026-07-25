package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.service.AnswerSubmissionService;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import com.careerdungeon.global.exception.BusinessException;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock
    UserRepository userRepository;

    @Mock
    ResumeRepository resumeRepository;

    @Mock
    PersonaConfigRepository personaConfigRepository;

    @Mock
    InterviewSessionRepository interviewSessionRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    QuestionRepository questionRepository;

    @Mock
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Mock
    QuestionGenerationPromptProvider promptProvider;

    @Mock
    ScoringPromptProvider scoringPromptProvider;

    @Mock
    LlmInvocationService llmInvocationService;

    @Mock
    AnswerSubmissionService answerSubmissionService;

    @Mock
    PlatformTransactionManager transactionManager;

    InterviewService sut;

    @BeforeEach
    void setUp() {
        sut = new InterviewService(
                userRepository,
                resumeRepository,
                personaConfigRepository,
                interviewSessionRepository,
                messageRepository,
                questionRepository,
                userUnlockStatusRepository,
                promptProvider,
                scoringPromptProvider,
                llmInvocationService,
                answerSubmissionService,
                transactionManager);
    }

    @Test
    void generateFollowUpQuestionRejectsCompletedSessionBeforeLlmCall() {
        InterviewSession session = interviewSession();
        session.complete();
        when(interviewSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sut.generateFollowUpQuestion(USER_ID, SESSION_ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("INTERVIEW_SESSION_INVALID_STATUS");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verifyNoInteractions(
                messageRepository,
                questionRepository,
                promptProvider,
                scoringPromptProvider,
                llmInvocationService);
    }

    @Test
    void generateFollowUpQuestionMapsConcurrentFollowUpUniqueConstraintToConflict() {
        InterviewSession session = interviewSession();
        when(interviewSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRoleAndTurn(SESSION_ID, MessageRole.QUESTION, 5))
                .thenReturn(false);
        stubQuestionAnswerPairs(session);
        stubInitialScoringPrompt();
        when(llmInvocationService.evaluateInitialAnswers(any(), any(LlmPrompt.class)))
                .thenReturn(new InitialEvaluationResponse(List.of(
                new QuestionEvaluation(1, 5, 2, 1, 1, 1, 0, "보완 필요"),
                new QuestionEvaluation(2, 20, 8, 4, 3, 3, 2, "충분"),
                new QuestionEvaluation(3, 18, 7, 4, 3, 2, 2, "충분"),
                new QuestionEvaluation(4, 20, 8, 4, 3, 3, 2, "충분")
        ), 63, 1, false));
        when(answerSubmissionService.scoreInitial(any())).thenReturn(initialEvaluation(
                new QuestionScore(1, 5, "보완 필요"),
                new QuestionScore(2, 20, "충분"),
                new QuestionScore(3, 18, "충분"),
                new QuestionScore(4, 20, "충분")));
        when(promptProvider.followUpPrompt(
                "STRICT",
                "한비",
                1,
                "question 1",
                "answer 1",
                "보완 필요"))
                .thenReturn(new QuestionGenerationPrompt("system prompt", "user prompt"));
        when(llmInvocationService.generateFollowUp(
                eq(1),
                eq("question 1"),
                eq("answer 1"),
                eq("보완 필요"),
                any(LlmPrompt.class)))
                .thenReturn(new FollowUpGenerationResponse("follow-up", "expected follow-up"));
        when(messageRepository.saveAndFlush(any(Message.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement; constraint [UQ_MESSAGES_SESSION_ROLE_TURN]"));

        assertThatThrownBy(() -> sut.generateFollowUpQuestion(USER_ID, SESSION_ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("FOLLOW_UP_ALREADY_EXISTS");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(messageRepository).saveAndFlush(any(Message.class));
        verify(llmInvocationService).generateFollowUp(
                eq(1),
                eq("question 1"),
                eq("answer 1"),
                eq("보완 필요"),
                argThat(prompt -> "system prompt".equals(prompt.systemPrompt())
                        && "user prompt".equals(prompt.userPrompt())));
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    void generateFollowUpQuestionUsesScoredWeakestQuestionInsteadOfRawWeakestQuestion() {
        InterviewSession session = interviewSession();
        when(interviewSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRoleAndTurn(SESSION_ID, MessageRole.QUESTION, 5))
                .thenReturn(false);
        stubQuestionAnswerPairs(session);
        stubInitialScoringPrompt();
        when(llmInvocationService.evaluateInitialAnswers(any(), any(LlmPrompt.class)))
                .thenReturn(new InitialEvaluationResponse(List.of(
                new QuestionEvaluation(1, 25, 10, 5, 4, 3, 3, "raw says strongest"),
                new QuestionEvaluation(2, 25, 30, 10, 10, 10, 10, "raw says weakest"),
                new QuestionEvaluation(3, 5, 2, 1, 1, 1, 0, "scored weakest"),
                new QuestionEvaluation(4, 20, 8, 4, 3, 3, 2, "충분")
        ), 75, 2, false));
        when(answerSubmissionService.scoreInitial(any())).thenReturn(initialEvaluation(
                new QuestionScore(1, 25, "raw says strongest"),
                new QuestionScore(2, 25, "raw says weakest"),
                new QuestionScore(3, 5, "scored weakest"),
                new QuestionScore(4, 20, "충분")));
        when(promptProvider.followUpPrompt(
                eq("STRICT"),
                anyString(),
                eq(3),
                eq("question 3"),
                eq("answer 3"),
                eq("scored weakest")))
                .thenReturn(new QuestionGenerationPrompt("system prompt", "user prompt"));
        when(llmInvocationService.generateFollowUp(
                eq(3),
                eq("question 3"),
                eq("answer 3"),
                eq("scored weakest"),
                any(LlmPrompt.class)))
                .thenReturn(new FollowUpGenerationResponse("follow-up", "expected follow-up"));
        when(messageRepository.saveAndFlush(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sut.generateFollowUpQuestion(USER_ID, SESSION_ID);

        verify(llmInvocationService).generateFollowUp(
                eq(3),
                eq("question 3"),
                eq("answer 3"),
                eq("scored weakest"),
                any(LlmPrompt.class));
    }

    @Test
    void generateFollowUpQuestionWhenScoringFailsDoesNotGenerateOrPersistFollowUp() {
        InterviewSession session = interviewSession();
        when(interviewSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRoleAndTurn(SESSION_ID, MessageRole.QUESTION, 5))
                .thenReturn(false);
        stubQuestionAnswerPairs(session);
        stubInitialScoringPrompt();
        when(llmInvocationService.evaluateInitialAnswers(any(), any(LlmPrompt.class)))
                .thenReturn(new InitialEvaluationResponse(List.of(
                new QuestionEvaluation(1, 25, 10, 5, 4, 3, 3, "feedback 1"),
                new QuestionEvaluation(2, 25, 10, 5, 4, 3, 3, "feedback 2")
        ), 50, 1, false));
        when(answerSubmissionService.scoreInitial(any()))
                .thenThrow(new LlmSchemaValidationException("평가 문항 구성"));

        assertThatThrownBy(() -> sut.generateFollowUpQuestion(USER_ID, SESSION_ID))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessageContaining("평가 문항 구성");

        verifyNoInteractions(promptProvider);
        verify(llmInvocationService, never()).generateFollowUp(anyInt(), any(), any(), any(), any());
        verify(messageRepository, never()).saveAndFlush(any(Message.class));
        verify(questionRepository, never()).save(any(Question.class));
    }

    private void stubQuestionAnswerPairs(InterviewSession session) {
        for (int turn = 1; turn <= 4; turn++) {
            Message question = message(session, MessageRole.QUESTION, "question " + turn, turn, (long) turn);
            Message answer = message(session, MessageRole.ANSWER, "answer " + turn, turn, 100L + turn);
            when(messageRepository.findBySession_IdAndRoleAndTurn(SESSION_ID, MessageRole.QUESTION, turn))
                    .thenReturn(Optional.of(question));
            when(messageRepository.findBySession_IdAndRoleAndTurn(SESSION_ID, MessageRole.ANSWER, turn))
                    .thenReturn(Optional.of(answer));
            when(questionRepository.findById(eq((long) turn))).thenReturn(Optional.of(new Question(
                    question,
                    "expected " + turn)));
        }
    }

    /** 채점 Provider가 조립한 프롬프트를 LLM 호출에 전달하는 테스트 공통 조건을 구성한다. */
    private void stubInitialScoringPrompt() {
        when(scoringPromptProvider.initialPrompt(any()))
                .thenReturn(new ScoringPrompt("scoring system prompt", "scoring user prompt"));
    }

    private InterviewSession interviewSession() {
        User user = new User("google-user", "user@example.com", "한비");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Resume resume = new Resume(USER_ID, ResumeType.RESUME, "resume.pdf", "hash");
        resume.markDone("resume text", Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(resume, "id", 20L);
        PersonaConfig personaConfig = new PersonaConfig(1, PersonaTone.STRICT);
        ReflectionTestUtils.setField(personaConfig, "id", 30L);
        InterviewSession session = new InterviewSession(user, resume, personaConfig, "DB");
        ReflectionTestUtils.setField(session, "id", SESSION_ID);
        return session;
    }

    private Message message(InterviewSession session, MessageRole role, String content, int turn, Long id) {
        Message message = new Message(session, role, content, turn);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private InitialJudgmentEvaluation initialEvaluation(
            QuestionScore first, QuestionScore second, QuestionScore third, QuestionScore fourth) {
        List<QuestionScore> scores = List.of(first, second, third, fourth);
        int totalScore = scores.stream().mapToInt(QuestionScore::score).sum();
        int weakestQuestionId = scores.stream()
                .min(java.util.Comparator.comparingInt(QuestionScore::score))
                .map(QuestionScore::questionId)
                .orElseThrow();
        return new InitialJudgmentEvaluation(scores, totalScore, weakestQuestionId, false);
    }
}
