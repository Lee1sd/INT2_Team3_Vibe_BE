package com.careerdungeon.domain.interview.controller;

import com.jayway.jsonpath.JsonPath;
import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.judgment.repository.AnswerScoreRepository;
import com.careerdungeon.domain.judgment.repository.JudgmentResultRepository;
import com.careerdungeon.domain.message.Message;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.model.StageGaugePolicy;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import com.careerdungeon.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ResumeRepository resumeRepository;

    @Autowired
    PersonaConfigRepository personaConfigRepository;

    @Autowired
    InterviewSessionRepository interviewSessionRepository;

    @Autowired
    MessageRepository messageRepository;

    @Autowired
    QuestionRepository questionRepository;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    AnswerScoreRepository answerScoreRepository;

    @Autowired
    JudgmentResultRepository judgmentResultRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        judgmentResultRepository.deleteAll();
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
    @DisplayName("IS-001: MockLlmClient로 세션 생성, 질문/모범답안 저장, 질문만 응답한다")
    void createInterviewGeneratesStoresAndReturnsQuestionsWithoutExpectedAnswer() throws Exception {
        User user = userRepository.saveAndFlush(new User("interview-user", "interview@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/interview.pdf",
                "hash-interview"));
        resume.markDone("Java, Spring Boot, DB 인덱스 성능 개선 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());

        MvcResult result = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "interviewerId": %d,
                                  "keyword": "DB"
                                }
                                """.formatted(resume.getId(), personaConfig.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0].questionId").isNumber())
                .andExpect(jsonPath("$.questions[0].question").isString())
                .andExpect(jsonPath("$.questions[0].expectedAnswer").doesNotExist())
                .andExpect(jsonPath("$.questions[1].questionId").isNumber())
                .andExpect(jsonPath("$.questions[2].questionId").isNumber())
                .andReturn();

        assertThat(interviewSessionRepository.findAll()).hasSize(1);
        List<Message> messages = messageRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Message::getTurn))
                .toList();
        assertThat(messages).hasSize(3)
                .allSatisfy(message -> {
                    assertThat(message.getRole()).isEqualTo(MessageRole.QUESTION);
                    assertThat(message.getContent()).isNotBlank();
                });
        assertThat(readQuestionIds(result)).containsExactly(1, 2, 3);
        assertThat(questionRepository.findAll()).hasSize(3)
                .allSatisfy(question -> {
                    assertThat(question.getMessageId()).isNotNull();
                    assertThat(question.getExpectedAnswer()).isNotBlank();
                });
    }

    @Test
    @DisplayName("IS-001: PORTFOLIO 타입 파일로는 면접 세션을 만들 수 없다")
    void createInterviewRejectsPortfolioResume() throws Exception {
        User user = userRepository.saveAndFlush(new User("portfolio-user", "portfolio@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.PORTFOLIO,
                "resumes/portfolio.pdf",
                "hash-portfolio"));
        resume.markDone("포트폴리오 텍스트", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.LENIENT));
        String token = jwtProvider.generateAccessToken(user.getId());

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "interviewerId": %d,
                                  "keyword": "DB"
                                }
                                """.formatted(resume.getId(), personaConfig.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESUME_TYPE_INVALID"));
    }

    @Test
    @DisplayName("IS-001: 다른 사용자의 이력서로는 면접 세션을 만들 수 없다")
    void createInterviewRejectsOtherUsersResume() throws Exception {
        User owner = userRepository.saveAndFlush(new User("owner-user", "owner@example.com", "소유자"));
        User requester = userRepository.saveAndFlush(new User("requester-user", "requester@example.com", "요청자"));
        saveUnlockStatus(requester);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                owner.getId(),
                ResumeType.RESUME,
                "resumes/owner.pdf",
                "hash-owner"));
        resume.markDone("소유자 이력서 텍스트", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.LENIENT));
        String token = jwtProvider.generateAccessToken(requester.getId());

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "interviewerId": %d,
                                  "keyword": "DB"
                                }
                                """.formatted(resume.getId(), personaConfig.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESUME_FORBIDDEN"));
    }

    @Test
    @DisplayName("IS-001: MVP 허용 목록에 없는 키워드는 명확한 에러로 거절한다")
    void createInterviewRejectsUnsupportedKeyword() throws Exception {
        User user = userRepository.saveAndFlush(new User("keyword-user", "keyword@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/keyword.pdf",
                "hash-keyword"));
        resume.markDone("Java, Spring Boot, 네트워크 장애 대응 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "interviewerId": %d,
                                  "keyword": "네트워크"
                                }
                                """.formatted(resume.getId(), personaConfig.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTERVIEW_KEYWORD_INVALID"))
                .andExpect(jsonPath("$.message").value("면접 키워드는 MVP 허용 목록(DB, 보안) 중 하나여야 합니다."));

        assertThat(interviewSessionRepository.findAll()).isEmpty();
        assertThat(messageRepository.findAll()).isEmpty();
        assertThat(questionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("IS-001: 해금되지 않은 면접관 레벨 요청은 거부한다")
    void createInterviewRejectsLockedInterviewerLevel() throws Exception {
        User user = userRepository.saveAndFlush(new User("locked-user", "locked@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/locked.pdf",
                "hash-locked"));
        resume.markDone("Java, Spring Boot, DB 트랜잭션 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig lockedPersona = personaConfigRepository.saveAndFlush(new PersonaConfig(2, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "interviewerId": %d,
                                  "keyword": "DB"
                                }
                                """.formatted(resume.getId(), lockedPersona.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INTERVIEWER_LOCKED"))
                .andExpect(jsonPath("$.message").value("아직 해금되지 않은 면접관입니다."));

        assertThat(interviewSessionRepository.findAll()).isEmpty();
        assertThat(messageRepository.findAll()).isEmpty();
        assertThat(questionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("IS-001: 해금된 면접관 레벨 요청은 정상 통과한다")
    void createInterviewAllowsUnlockedInterviewerLevel() throws Exception {
        User user = userRepository.saveAndFlush(new User("unlocked-user", "unlocked@example.com", "홍길동"));
        saveUnlockStatus(user, StageGaugePolicy.STAGE_1);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/unlocked.pdf",
                "hash-unlocked"));
        resume.markDone("Java, Spring Boot, 보안 인증 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig unlockedPersona = personaConfigRepository.saveAndFlush(new PersonaConfig(2, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());

        mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "interviewerId": %d,
                                  "keyword": "보안"
                                }
                                """.formatted(resume.getId(), unlockedPersona.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.questions.length()").value(3));

        assertThat(interviewSessionRepository.findAll()).hasSize(1);
        assertThat(messageRepository.findAll()).hasSize(3);
        assertThat(questionRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("IS-002: 최초 3개 답변 제출은 답변·최초점수·꼬리질문을 저장하고 세부 루브릭 없이 응답한다")
    void submitInitialAnswersScoresAndReturnsFollowUpWithoutRubrics() throws Exception {
        User user = userRepository.saveAndFlush(new User("answer-initial-user", "answer-initial@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/answer-initial.pdf",
                "hash-answer-initial"));
        resume.markDone("Java, Spring Boot, DB 인덱스 성능 개선 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());
        CreatedInterview created = createSession(token, resume.getId(), personaConfig.getId());
        long sessionId = created.sessionId();

        MvcResult result = mockMvc.perform(post("/api/interviews/{id}/answers", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialAnswersJson(created.questionIds())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(3))
                .andExpect(jsonPath("$.evaluations[0].questionId").value(1))
                .andExpect(jsonPath("$.evaluations[0].score").value(18))
                .andExpect(jsonPath("$.evaluations[0].feedback").isString())
                .andExpect(jsonPath("$.evaluations[0].technicalAccuracy").doesNotExist())
                .andExpect(jsonPath("$.totalScore").value(54))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.overallFeedback").doesNotExist())
                .andExpect(jsonPath("$.nextTurn.type").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.nextTurn.question").isString())
                .andReturn();

        int weakestQuestionId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.weakestQuestionId")).intValue();
        int targetQuestionId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.nextTurn.targetQuestionId")).intValue();
        assertThat(created.questionIds()).contains(weakestQuestionId);
        assertThat(targetQuestionId).isEqualTo(weakestQuestionId);

        assertThat(messageRepository.findAll()).hasSize(7);
        assertThat(messageRepository.findBySession_IdAndRoleAndTurn(sessionId, MessageRole.QUESTION, 4))
                .isPresent();
        assertThat(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(sessionId))
                .hasSize(3)
                .extracting(score -> score.getTurn())
                .containsExactly(1, 2, 3);
        assertThat(interviewSessionRepository.findById(sessionId).orElseThrow().getStatus().name())
                .isEqualTo("AWAITING_FOLLOWUP");
    }

    @Test
    @DisplayName("IS-002b: 꼬리질문 답변 제출은 최종판정 저장 후 세션을 완료한다")
    void submitFinalAnswerScoresAndCompletesSession() throws Exception {
        User user = userRepository.saveAndFlush(new User("answer-final-user", "answer-final@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/answer-final.pdf",
                "hash-answer-final"));
        resume.markDone("Java, Spring Boot, DB 인덱스 성능 개선 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());
        CreatedInterview created = createSession(token, resume.getId(), personaConfig.getId());
        long sessionId = created.sessionId();
        submitInitialAnswers(token, created);

        mockMvc.perform(post("/api/interviews/{id}/answers", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finalAnswerJson(4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(4))
                .andExpect(jsonPath("$.evaluations[3].questionId").value(4))
                .andExpect(jsonPath("$.evaluations[3].score").value(18))
                .andExpect(jsonPath("$.evaluations[3].feedback").isString())
                .andExpect(jsonPath("$.evaluations[3].technicalAccuracy").doesNotExist())
                .andExpect(jsonPath("$.totalScore").value(72))
                .andExpect(jsonPath("$.weakestQuestionId").doesNotExist())
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.overallFeedback").isString())
                .andExpect(jsonPath("$.nextTurn").doesNotExist());

        assertThat(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(sessionId))
                .hasSize(4)
                .extracting(score -> score.getTurn())
                .containsExactly(1, 2, 3, 4);
        assertThat(judgmentResultRepository.existsBySession_Id(sessionId)).isTrue();
        assertThat(interviewSessionRepository.findById(sessionId).orElseThrow().getStatus().name())
                .isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("IS-002: 최초 답변 채점 완료 후 같은 최초 답변을 다시 제출하면 거부한다")
    void submitInitialAnswersRejectsSecondInitialSubmissionAfterScored() throws Exception {
        User user = userRepository.saveAndFlush(new User("answer-initial-duplicate-user", "answer-initial-duplicate@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/answer-initial-duplicate.pdf",
                "hash-answer-initial-duplicate"));
        resume.markDone("Java, Spring Boot, DB 인덱스 성능 개선 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());
        CreatedInterview created = createSession(token, resume.getId(), personaConfig.getId());
        long sessionId = created.sessionId();
        submitInitialAnswers(token, created);

        mockMvc.perform(post("/api/interviews/{id}/answers", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialAnswersJson(created.questionIds())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVIEW_ANSWER_ALREADY_SUBMITTED"));
    }

    @Test
    @DisplayName("IS-002b: 최종 판정 완료 후 같은 꼬리질문 답변을 다시 제출하면 거부한다")
    void submitFinalAnswerRejectsSecondFinalSubmissionAfterCompleted() throws Exception {
        User user = userRepository.saveAndFlush(new User("answer-final-duplicate-user", "answer-final-duplicate@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/answer-final-duplicate.pdf",
                "hash-answer-final-duplicate"));
        resume.markDone("Java, Spring Boot, DB 인덱스 성능 개선 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());
        CreatedInterview created = createSession(token, resume.getId(), personaConfig.getId());
        long sessionId = created.sessionId();
        submitInitialAnswers(token, created);
        submitFinalAnswer(token, sessionId);

        mockMvc.perform(post("/api/interviews/{id}/answers", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finalAnswerJson(4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVIEW_ANSWER_ALREADY_SUBMITTED"));
    }

    @Test
    @DisplayName("IS-002: COMPLETED 상태에서는 답변 제출을 거부한다")
    void submitAnswersRejectsCompletedSession() throws Exception {
        User user = userRepository.saveAndFlush(new User("answer-completed-user", "answer-completed@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/answer-completed.pdf",
                "hash-answer-completed"));
        resume.markDone("Java, Spring Boot, DB 인덱스 성능 개선 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());
        CreatedInterview created = createSession(token, resume.getId(), personaConfig.getId());
        long sessionId = created.sessionId();
        submitInitialAnswers(token, created);
        submitFinalAnswer(token, sessionId);

        mockMvc.perform(post("/api/interviews/{id}/answers", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialAnswersJson(created.questionIds())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVIEW_ANSWER_ALREADY_SUBMITTED"));
    }

    @Test
    @DisplayName("IS-002b: IN_PROGRESS 상태에서 turn 4 답변 제출을 거부한다")
    void submitFinalAnswerRejectsInProgressSession() throws Exception {
        User user = userRepository.saveAndFlush(new User("answer-in-progress-user", "answer-in-progress@example.com", "홍길동"));
        saveUnlockStatus(user);
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/answer-in-progress.pdf",
                "hash-answer-in-progress"));
        resume.markDone("Java, Spring Boot, DB 인덱스 성능 개선 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        String token = jwtProvider.generateAccessToken(user.getId());
        CreatedInterview created = createSession(token, resume.getId(), personaConfig.getId());
        long sessionId = created.sessionId();
        InterviewSession session = interviewSessionRepository.findById(sessionId).orElseThrow();
        Message followUpMessage = messageRepository.saveAndFlush(new Message(
                session,
                MessageRole.QUESTION,
                "follow-up question",
                4));

        mockMvc.perform(post("/api/interviews/{id}/answers", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finalAnswerJson(4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INTERVIEW_SESSION_INVALID_STATUS"));
    }

    private void saveUnlockStatus(User user, StageGaugePolicy... completedStages) {
        UserUnlockStatus unlockStatus = UserUnlockStatus.initialFor(user);
        for (StageGaugePolicy completedStage : completedStages) {
            unlockStatus.completeStage(completedStage);
        }
        jdbcTemplate.update(
                "insert into user_unlock_status (user_id, unlocked_level, progress_gauge) values (?, ?, ?)",
                user.getId(),
                unlockStatus.getUnlockedLevel(),
                unlockStatus.getProgressGauge());
    }

    private List<Integer> readQuestionIds(MvcResult result) throws Exception {
        List<Number> questionIds = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.questions[*].questionId");
        return questionIds.stream()
                .map(Number::intValue)
                .toList();
    }

    private CreatedInterview createSession(String token, Long resumeId, Long personaConfigId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/interviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "interviewerId": %d,
                                  "keyword": "DB"
                                }
                                """.formatted(resumeId, personaConfigId)))
                .andExpect(status().isCreated())
                .andReturn();
        long sessionId = ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.sessionId")).longValue();
        return new CreatedInterview(sessionId, readQuestionIds(result));
    }

    private void submitInitialAnswers(String token, CreatedInterview created) throws Exception {
        mockMvc.perform(post("/api/interviews/{id}/answers", created.sessionId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialAnswersJson(created.questionIds())))
                .andExpect(status().isOk());
    }

    private void submitFinalAnswer(String token, long sessionId) throws Exception {
        mockMvc.perform(post("/api/interviews/{id}/answers", sessionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finalAnswerJson(4)))
                .andExpect(status().isOk());
    }

    private String initialAnswersJson(List<Integer> questionIds) {
        return """
                {
                  "answers": [
                    { "questionId": %d, "answerText": "GC는 Young/Old 세대를 기준으로 동작합니다." },
                    { "questionId": %d, "answerText": "인덱스는 조회 조건과 정렬에 맞춰 사용합니다." },
                    { "questionId": %d, "answerText": "REST는 자원 중심 URI와 HTTP 메서드를 사용합니다." }
                  ]
                }
                """.formatted(questionIds.get(0), questionIds.get(1), questionIds.get(2));
    }

    private String finalAnswerJson(Integer questionId) {
        return """
                {
                  "answers": [
                    { "questionId": %d, "answerText": "부족했던 판단 근거와 실무 적용 예시를 보완합니다." }
                  ]
                }
                """.formatted(questionId);
    }

    private record CreatedInterview(
            long sessionId,
            List<Integer> questionIds) {
    }

}
