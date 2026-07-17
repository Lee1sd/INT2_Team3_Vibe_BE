package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.dto.InterviewAnswerItemRequest;
import com.careerdungeon.domain.interview.dto.InterviewAnswerSubmitRequest;
import com.careerdungeon.domain.interview.dto.InterviewAnswerSubmitResponse;
import com.careerdungeon.domain.interview.dto.InterviewCreateRequest;
import com.careerdungeon.domain.interview.dto.InterviewCreateResponse;
import com.careerdungeon.domain.interview.dto.InterviewQuestionResponse;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.judgment.repository.AnswerScoreRepository;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import com.careerdungeon.global.exception.BusinessException;
import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InterviewServiceIntegrationTest {

    @Autowired
    InterviewService sut;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ResumeRepository resumeRepository;

    @Autowired
    PersonaConfigRepository personaConfigRepository;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    InterviewSessionRepository interviewSessionRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    AnswerScoreRepository answerScoreRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        answerScoreRepository.deleteAll();
        questionRepository.deleteAll();
        messageRepository.deleteAll();
        interviewSessionRepository.deleteAll();
        resumeRepository.deleteAll();
        userUnlockStatusRepository.deleteAll();
        personaConfigRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("IS-001: parseStatus가 DONE이 아니면 면접 세션 생성을 차단한다")
    void createInterviewRejectsResumeBeforeParseDone() {
        User user = saveUserWithUnlockStatus("processing-user");
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/processing.pdf",
                "hash-processing"));
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.LENIENT));

        assertThatThrownBy(() -> sut.createInterview(
                        user.getId(),
                        new InterviewCreateRequest(resume.getId(), personaConfig.getId(), "DB")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이력서 파싱이 완료된 뒤 면접 세션을 생성할 수 있습니다.");

        assertThat(interviewSessionRepository.findAll()).isEmpty();
        assertThat(messageRepository.findAll()).isEmpty();
        assertThat(questionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("IS-001: LLM 질문 배열은 turn 기준으로 저장하고 응답한다")
    void createInterviewSortsGeneratedQuestionsByTurn() {
        User user = saveUserWithUnlockStatus("order-user");
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/order.pdf",
                "hash-order"));
        resume.markDone("DB 인덱스와 트랜잭션 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));

        InterviewCreateResponse response = sut.createInterview(
                user.getId(),
                new InterviewCreateRequest(resume.getId(), personaConfig.getId(), "DB"));

        assertThat(response.questions())
                .extracting(question -> question.question())
                .containsExactly("turn1 question", "turn2 question", "turn3 question");

        List<Message> messagesByTurn = messageRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Message::getTurn))
                .toList();
        assertThat(response.questions())
                .extracting(question -> question.questionId())
                .containsExactlyElementsOf(messagesByTurn.stream()
                        .map(Message::getId)
                        .toList());
    }

    @Test
    @DisplayName("IS-002a: 최저점 문항을 식별해 꼬리질문 turn=4와 모범답안을 저장한다")
    void generateFollowUpQuestionEvaluatesWeakestQuestionAndPersistsTurn4() {
        User user = saveUserWithUnlockStatus("follow-up-user");
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/follow-up.pdf",
                "hash-follow-up"));
        resume.markDone("Redis 캐시와 MySQL 동기화 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        InterviewCreateResponse created = sut.createInterview(
                user.getId(),
                new InterviewCreateRequest(resume.getId(), personaConfig.getId(), "DB"));

        messageRepository.saveAndFlush(new Message(
                interviewSessionRepository.findById(created.sessionId()).orElseThrow(),
                MessageRole.ANSWER,
                "답변1",
                1));
        messageRepository.saveAndFlush(new Message(
                interviewSessionRepository.findById(created.sessionId()).orElseThrow(),
                MessageRole.ANSWER,
                "캐시는 DB 부하를 줄여주기 때문에 사용했습니다.",
                2));
        messageRepository.saveAndFlush(new Message(
                interviewSessionRepository.findById(created.sessionId()).orElseThrow(),
                MessageRole.ANSWER,
                "답변3",
                3));

        InterviewQuestionResponse followUp = sut.generateFollowUpQuestion(user.getId(), created.sessionId());

        Message followUpMessage = messageRepository.findBySession_IdAndRoleAndTurn(
                        created.sessionId(),
                        MessageRole.QUESTION,
                        4)
                .orElseThrow();
        assertThat(followUp.questionId()).isEqualTo(followUpMessage.getId());
        assertThat(followUp.question()).isEqualTo("turn2 question 보완 질문");
        assertThat(followUpMessage.getContent()).isEqualTo("turn2 question 보완 질문");
        assertThat(questionRepository.findById(followUpMessage.getId()).orElseThrow().getExpectedAnswer())
                .isEqualTo("정합성 처리 전략이 빠져 있습니다. 보완 모범답안");
        assertThat(interviewSessionRepository.findById(created.sessionId()).orElseThrow().getStatus().name())
                .isEqualTo("AWAITING_FOLLOWUP");
    }

    @Test
    @DisplayName("IS-002: 답변 저장 후 LLM 호출이 실패해도 재시도 시 기존 답변으로 채점을 이어간다")
    void submitInitialAnswersRetriesFromStoredAnswersAfterLlmFailure() {
        User user = saveUserWithUnlockStatus("retry-after-llm-failure-user");
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/retry-after-llm-failure.pdf",
                "hash-retry-after-llm-failure"));
        resume.markDone("DB 인덱스와 트랜잭션 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        InterviewCreateResponse created = sut.createInterview(
                user.getId(),
                new InterviewCreateRequest(resume.getId(), personaConfig.getId(), "DB"));
        InterviewAnswerSubmitRequest request = initialAnswerSubmitRequest(
                created.questions().stream()
                        .map(InterviewQuestionResponse::questionId)
                        .toList(),
                "LLM 실패 후 재시도 답변");

        assertThatThrownBy(() -> sut.submitAnswers(user.getId(), created.sessionId(), request))
                .isInstanceOf(RuntimeException.class);
        assertThat(messageRepository.findAll().stream()
                .filter(message -> message.getRole() == MessageRole.ANSWER)
                .toList()).hasSize(3);
        assertThat(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(created.sessionId())).isEmpty();
        assertThat(interviewSessionRepository.findById(created.sessionId()).orElseThrow().getStatus().name())
                .isEqualTo("IN_PROGRESS");

        InterviewAnswerSubmitResponse response = sut.submitAnswers(user.getId(), created.sessionId(), request);

        assertThat(response.totalScore()).isEqualTo(43);
        assertThat(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(created.sessionId()))
                .hasSize(3)
                .extracting(score -> score.getTurn())
                .containsExactly(1, 2, 3);
        assertThat(messageRepository.findAll().stream()
                .filter(message -> message.getRole() == MessageRole.ANSWER)
                .toList()).hasSize(3);
        assertThat(interviewSessionRepository.findById(created.sessionId()).orElseThrow().getStatus().name())
                .isEqualTo("AWAITING_FOLLOWUP");
    }

    private User saveUserWithUnlockStatus(String googleId) {
        User user = userRepository.saveAndFlush(new User(googleId, googleId + "@example.com", "한비"));
        UserUnlockStatus unlockStatus = UserUnlockStatus.initialFor(user);
        jdbcTemplate.update(
                "insert into user_unlock_status (user_id, unlocked_level, progress_gauge) values (?, ?, ?)",
                user.getId(),
                unlockStatus.getUnlockedLevel(),
                unlockStatus.getProgressGauge());
        return user;
    }

    private InterviewAnswerSubmitRequest initialAnswerSubmitRequest(List<Long> questionIds, String answerPrefix) {
        return new InterviewAnswerSubmitRequest(List.of(
                new InterviewAnswerItemRequest(questionIds.get(0), answerPrefix + " 1"),
                new InterviewAnswerItemRequest(questionIds.get(1), answerPrefix + " 2"),
                new InterviewAnswerItemRequest(questionIds.get(2), answerPrefix + " 3")));
    }

    @TestConfiguration
    static class OutOfOrderLlmClientConfig {

        @Bean
        @Primary
        LlmClient outOfOrderLlmClient() {
            return new LlmClient() {
                private final AtomicBoolean retryFailureTriggered = new AtomicBoolean(false);

                @Override
                public QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request) {
                    return new QuestionGenerationResponse(List.of(
                            new GeneratedQuestion(2, "turn2 question", "turn2 answer"),
                            new GeneratedQuestion(1, "turn1 question", "turn1 answer"),
                            new GeneratedQuestion(3, "turn3 question", "turn3 answer")
                    ));
                }

                @Override
                public InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request) {
                    if (request.questionAnswerPairs().stream()
                        .anyMatch(pair -> pair.userAnswer().contains("LLM 실패 후 재시도"))
                            && retryFailureTriggered.compareAndSet(false, true)) {
                        throw new RuntimeException("테스트용 최초 LLM 호출 실패");
                    }
                    return new InitialEvaluationResponse(List.of(
                            new QuestionEvaluation(1, 20, 8, 4, 3, 3, 2, "충분합니다."),
                            new QuestionEvaluation(2, 5, 2, 1, 1, 1, 0, "정합성 처리 전략이 빠져 있습니다."),
                            new QuestionEvaluation(3, 18, 7, 4, 3, 2, 2, "대체로 충분합니다.")
                    ), 43, 2, false);
                }

                @Override
                public FollowUpGenerationResponse generateFollowUp(
                        int weakestQuestionId,
                        String questionText,
                        String userAnswer,
                        String feedback) {
                    return new FollowUpGenerationResponse(
                            questionText + " 보완 질문",
                            feedback + " 보완 모범답안");
                }

                @Override
                public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request) {
                    throw new UnsupportedOperationException("not used in question generation tests");
                }
            };
        }
    }
}
