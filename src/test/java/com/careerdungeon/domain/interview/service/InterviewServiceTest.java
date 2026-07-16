package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    LlmInvocationService llmInvocationService;

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
                llmInvocationService);
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

        verifyNoInteractions(messageRepository, questionRepository, promptProvider, llmInvocationService);
    }

    @Test
    void generateFollowUpQuestionMapsConcurrentFollowUpUniqueConstraintToConflict() {
        InterviewSession session = interviewSession();
        when(interviewSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRoleAndTurn(SESSION_ID, MessageRole.QUESTION, 4))
                .thenReturn(false);
        stubQuestionAnswerPairs(session);
        when(llmInvocationService.evaluateInitialAnswers(any())).thenReturn(new InitialEvaluationResponse(List.of(
                new QuestionEvaluation(1, 5, 2, 1, 1, 1, 0, "보완 필요"),
                new QuestionEvaluation(2, 20, 8, 4, 3, 3, 2, "충분"),
                new QuestionEvaluation(3, 18, 7, 4, 3, 2, 2, "충분")
        ), 43, 1, false));
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

    private void stubQuestionAnswerPairs(InterviewSession session) {
        for (int turn = 1; turn <= 3; turn++) {
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
}
