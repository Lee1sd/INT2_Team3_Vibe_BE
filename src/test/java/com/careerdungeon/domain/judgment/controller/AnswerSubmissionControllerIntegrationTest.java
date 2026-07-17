package com.careerdungeon.domain.judgment.controller;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.dto.InterviewCreateRequest;
import com.careerdungeon.domain.interview.dto.InterviewCreateResponse;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.interview.repository.QuestionRepository;
import com.careerdungeon.domain.interview.service.InterviewService;
import com.careerdungeon.domain.judgment.repository.AnswerScoreRepository;
import com.careerdungeon.domain.judgment.repository.JudgmentResultRepository;
import com.careerdungeon.domain.message.MessageRepository;
import com.careerdungeon.domain.message.MessageRole;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.repository.BadgeRepository;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mock LLM으로 IS-002 최초 제출부터 최종 판정까지 실제 HTTP 흐름을 검증한다. */
@SpringBootTest(properties = "llm.mock.score-per-question=20")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnswerSubmissionControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    InterviewService interviewService;

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
    JudgmentResultRepository judgmentResultRepository;

    @Autowired
    BadgeRepository badgeRepository;

    @Autowired
    UserBadgeRepository userBadgeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        userBadgeRepository.deleteAll();
        judgmentResultRepository.deleteAll();
        answerScoreRepository.deleteAll();
        questionRepository.deleteAll();
        messageRepository.deleteAll();
        interviewSessionRepository.deleteAll();
        resumeRepository.deleteAll();
        userUnlockStatusRepository.deleteAll();
        badgeRepository.deleteAll();
        personaConfigRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("IS-002/IS-002b: 최초 세 답변과 turn 4를 제출하면 판정·진행도·뱃지가 함께 반영된다")
    void submitInitialAndFollowUpAnswersCompletesJudgmentFlow() throws Exception {
        TestFixture fixture = createFixture(true);

        mockMvc.perform(post("/api/interviews/{id}/answers", fixture.sessionId())
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialAnswersJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(3))
                .andExpect(jsonPath("$.totalScore").value(60))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.weakestQuestionId").isNumber())
                .andExpect(jsonPath("$.nextTurn.type").value("FOLLOW_UP"))
                .andExpect(jsonPath("$.nextTurn.question").isNotEmpty());

        assertThat(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(fixture.sessionId()))
                .hasSize(3)
                .extracting(score -> score.getTurn())
                .containsExactly(1, 2, 3);
        assertThat(interviewSessionRepository.findById(fixture.sessionId()).orElseThrow().getStatus().name())
                .isEqualTo("AWAITING_FOLLOWUP");

        mockMvc.perform(post("/api/interviews/{id}/answers", fixture.sessionId())
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpAnswerJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluations.length()").value(4))
                .andExpect(jsonPath("$.evaluations[0].feedback").doesNotExist())
                .andExpect(jsonPath("$.evaluations[3].questionId").value(4))
                .andExpect(jsonPath("$.evaluations[3].feedback").isNotEmpty())
                .andExpect(jsonPath("$.totalScore").value(80))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.overallFeedback").isNotEmpty())
                .andExpect(jsonPath("$.nextTurn").isEmpty());

        assertThat(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(fixture.sessionId()))
                .hasSize(4)
                .extracting(score -> score.getTurn())
                .containsExactly(1, 2, 3, 4);
        assertThat(judgmentResultRepository.findAll()).singleElement().satisfies(result -> {
            assertThat(result.getTotalScore()).isEqualTo(80);
            assertThat(result.isPassed()).isTrue();
        });
        assertThat(interviewSessionRepository.findById(fixture.sessionId()).orElseThrow().getStatus().name())
                .isEqualTo("COMPLETED");
        UserUnlockStatus progress = userUnlockStatusRepository.findById(fixture.userId()).orElseThrow();
        assertThat(progress.getUnlockedLevel()).isEqualTo(2);
        assertThat(progress.getProgressGauge()).isEqualTo(30);
        assertThat(userBadgeRepository.countByUserId(fixture.userId())).isEqualTo(1);

        mockMvc.perform(post("/api/interviews/{id}/answers", fixture.sessionId())
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpAnswerJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JUDGMENT_ALREADY_COMPLETED"));
        assertThat(judgmentResultRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("IS-002b: 뱃지 기준 데이터가 없어 진행도 반영이 실패하면 최종 제출 전체를 롤백한다")
    void finalSubmissionRollsBackWhenProgressionFails() throws Exception {
        TestFixture fixture = createFixture(false);
        mockMvc.perform(post("/api/interviews/{id}/answers", fixture.sessionId())
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialAnswersJson()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/interviews/{id}/answers", fixture.sessionId())
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(followUpAnswerJson()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("BADGE_NOT_FOUND"));

        assertThat(messageRepository.findBySession_IdAndRoleAndTurn(
                fixture.sessionId(), MessageRole.ANSWER, 4)).isEmpty();
        assertThat(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(fixture.sessionId()))
                .hasSize(3);
        assertThat(judgmentResultRepository.findAll()).isEmpty();
        assertThat(interviewSessionRepository.findById(fixture.sessionId()).orElseThrow().getStatus().name())
                .isEqualTo("AWAITING_FOLLOWUP");
        UserUnlockStatus progress = userUnlockStatusRepository.findById(fixture.userId()).orElseThrow();
        assertThat(progress.getUnlockedLevel()).isEqualTo(1);
        assertThat(progress.getProgressGauge()).isZero();
    }

    @Test
    @DisplayName("IS-002: 최초 답변 문항 구성이 1,2,3이 아니면 저장과 LLM 호출 전에 거부한다")
    void initialSubmissionRejectsInvalidQuestionSet() throws Exception {
        TestFixture fixture = createFixture(false);

        mockMvc.perform(post("/api/interviews/{id}/answers", fixture.sessionId())
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": 1, "answerText": "첫 번째 답변"},
                                    {"questionId": 2, "answerText": "두 번째 답변"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JUDGMENT_QUESTION_SET_INVALID"));

        assertThat(messageRepository.findAll().stream()
                .filter(message -> message.getRole() == MessageRole.ANSWER))
                .isEmpty();
        assertThat(answerScoreRepository.findAll()).isEmpty();
    }

    /** 사용자·진행도·이력서·페르소나·질문 세 문항을 실제 저장해 테스트 세션을 만든다. */
    private TestFixture createFixture(boolean withStageTwoBadge) {
        User user = userRepository.saveAndFlush(new User(
                "judgment-flow-" + withStageTwoBadge,
                "judgment-" + withStageTwoBadge + "@example.com",
                "용성"));
        UserUnlockStatus unlockStatus = UserUnlockStatus.initialFor(user);
        jdbcTemplate.update(
                "insert into user_unlock_status (user_id, unlocked_level, progress_gauge) values (?, ?, ?)",
                user.getId(),
                unlockStatus.getUnlockedLevel(),
                unlockStatus.getProgressGauge());
        if (withStageTwoBadge) {
            badgeRepository.saveAndFlush(Badge.create(2, "Stage2 테스트", "/badges/stage2.png"));
        }

        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/judgment.pdf",
                "hash-judgment-" + withStageTwoBadge));
        resume.markDone("Spring Boot와 DB 트랜잭션 경험", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig persona = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        InterviewCreateResponse created = interviewService.createInterview(
                user.getId(),
                new InterviewCreateRequest(resume.getId(), persona.getId(), "DB"));
        return new TestFixture(user.getId(), created.sessionId(), jwtProvider.generateAccessToken(user.getId()));
    }

    private String initialAnswersJson() {
        return """
                {
                  "answers": [
                    {"questionId": 1, "answerText": "첫 번째 답변입니다."},
                    {"questionId": 2, "answerText": "두 번째 답변입니다."},
                    {"questionId": 3, "answerText": "세 번째 답변입니다."}
                  ]
                }
                """;
    }

    private String followUpAnswerJson() {
        return """
                {
                  "answers": [
                    {"questionId": 4, "answerText": "판단 근거와 실무 사례를 보완한 답변입니다."}
                  ]
                }
                """;
    }

    /** 테스트에 필요한 사용자·세션·인증 토큰을 묶는다. */
    private record TestFixture(long userId, long sessionId, String token) {
    }
}
