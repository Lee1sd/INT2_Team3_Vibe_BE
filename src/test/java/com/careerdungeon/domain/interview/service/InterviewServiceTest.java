package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.entity.Question;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
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
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock UserRepository userRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock PersonaConfigRepository personaConfigRepository;
    @Mock InterviewSessionRepository interviewSessionRepository;
    @Mock MessageRepository messageRepository;
    @Mock QuestionRepository questionRepository;
    @Mock UserUnlockStatusRepository userUnlockStatusRepository;
    @Mock QuestionGenerationPromptProvider promptProvider;
    @Mock LlmInvocationService llmInvocationService;

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
    void persistFollowUpQuestionRejectsCompletedSession() {
        InterviewSession session = interviewSession();
        session.complete();
        when(interviewSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sut.persistGeneratedFollowUpQuestion(
                USER_ID,
                SESSION_ID,
                new FollowUpGenerationResponse("follow-up", "expected follow-up")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INTERVIEW_SESSION_INVALID_STATUS");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verifyNoInteractions(messageRepository, questionRepository, promptProvider, llmInvocationService);
    }

    @Test
    void persistFollowUpQuestionMapsConcurrentUniqueConstraintToConflict() {
        InterviewSession session = interviewSession();
        when(interviewSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(messageRepository.existsBySession_IdAndRoleAndTurn(SESSION_ID, MessageRole.QUESTION, 4))
                .thenReturn(false);
        when(messageRepository.saveAndFlush(any(Message.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement; constraint [UQ_MESSAGES_SESSION_ROLE_TURN]"));

        assertThatThrownBy(() -> sut.persistGeneratedFollowUpQuestion(
                USER_ID,
                SESSION_ID,
                new FollowUpGenerationResponse("follow-up", "expected follow-up")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("FOLLOW_UP_ALREADY_EXISTS");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(messageRepository).saveAndFlush(any(Message.class));
        verify(questionRepository, never()).save(any(Question.class));
        verifyNoInteractions(promptProvider, llmInvocationService);
    }

    @Test
    void generateFollowUpQuestionUsesServerScoredWeakestQuestion() {
        when(promptProvider.followUpPrompt(
                eq("STRICT"),
                eq("user"),
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

        FollowUpGenerationResponse result = sut.generateFollowUpQuestionContent(
                questionAnswerPairs(),
                "STRICT",
                "user",
                initialEvaluation(3));

        assertThat(result.followUpQuestion()).isEqualTo("follow-up");
        verify(llmInvocationService).generateFollowUp(
                eq(3),
                eq("question 3"),
                eq("answer 3"),
                eq("scored weakest"),
                argThat(prompt -> "system prompt".equals(prompt.systemPrompt())
                        && "user prompt".equals(prompt.userPrompt())));
    }

    @Test
    void generateFollowUpQuestionRejectsIncompleteContextBeforeLlmCall() {
        assertThatThrownBy(() -> sut.generateFollowUpQuestionContent(
                questionAnswerPairs().subList(0, 2),
                "STRICT",
                "user",
                initialEvaluation(1)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FOLLOW_UP_CONTEXT_INVALID"));

        verifyNoInteractions(promptProvider, llmInvocationService);
    }

    /** 꼬리질문 생성 단위 테스트에서 사용할 최초 3문항 문맥을 만든다. */
    private List<QuestionAnswerPair> questionAnswerPairs() {
        return List.of(
                new QuestionAnswerPair(1, "question 1", "answer 1", "expected 1"),
                new QuestionAnswerPair(2, "question 2", "answer 2", "expected 2"),
                new QuestionAnswerPair(3, "question 3", "answer 3", "expected 3"));
    }

    /** 서버 채점 결과의 최저점 문항을 명시한 최초 판정 값을 만든다. */
    private InitialJudgmentEvaluation initialEvaluation(int weakestQuestionId) {
        return new InitialJudgmentEvaluation(List.of(
                new QuestionScore(1, 20, "feedback 1"),
                new QuestionScore(2, 20, "feedback 2"),
                new QuestionScore(3, 5, "scored weakest")),
                45,
                weakestQuestionId,
                false);
    }

    /** 영속화 단위 테스트에서 사용할 진행 중 면접 세션을 만든다. */
    private InterviewSession interviewSession() {
        User user = new User("google-user", "user@example.com", "user");
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
}
