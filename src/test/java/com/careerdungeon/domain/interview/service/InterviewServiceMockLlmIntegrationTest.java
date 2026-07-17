package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.dto.InterviewCreateRequest;
import com.careerdungeon.domain.interview.dto.InterviewCreateResponse;
import com.careerdungeon.domain.interview.dto.InterviewQuestionResponse;
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
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.mock.MockLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InterviewServiceMockLlmIntegrationTest {

    @Autowired
    InterviewService sut;

    @Autowired
    LlmClient llmClient;

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
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        questionRepository.deleteAll();
        messageRepository.deleteAll();
        interviewSessionRepository.deleteAll();
        resumeRepository.deleteAll();
        userUnlockStatusRepository.deleteAll();
        personaConfigRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Mock LLM 기준: 최저점 식별 후 꼬리질문 turn=4와 모범답안을 저장한다")
    void generateFollowUpQuestionWithMockLlmPersistsTurn4Question() {
        assertThat(llmClient).isInstanceOf(MockLlmClient.class);
        User user = saveUserWithUnlockStatus("mock-follow-up-user");
        Resume resume = resumeRepository.saveAndFlush(new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/mock-follow-up.pdf",
                "hash-mock-follow-up"));
        resume.markDone("Spring Boot 기반 게시판 프로젝트에서 Redis 캐시를 도입", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig personaConfig = personaConfigRepository.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT));
        InterviewCreateResponse created = sut.createInterview(
                user.getId(),
                new InterviewCreateRequest(resume.getId(), personaConfig.getId(), "DB"));

        var session = interviewSessionRepository.findById(created.sessionId()).orElseThrow();
        messageRepository.saveAndFlush(new Message(session, MessageRole.ANSWER, "답변1", 1));
        messageRepository.saveAndFlush(new Message(
                session,
                MessageRole.ANSWER,
                "캐시는 DB 부하를 줄여주기 때문에 사용했습니다.",
                2));
        messageRepository.saveAndFlush(new Message(session, MessageRole.ANSWER, "답변3", 3));

        InitialJudgmentEvaluation scoredInitial = new InitialJudgmentEvaluation(List.of(
                new QuestionScore(1, 20, "피드백 1"),
                new QuestionScore(2, 5, "피드백 2"),
                new QuestionScore(3, 18, "피드백 3")),
                43,
                2,
                false);
        FollowUpGenerationResponse generated = sut.generateFollowUpQuestionContent(
                initialPairs(created.sessionId()),
                PersonaTone.STRICT.name(),
                user.getName(),
                scoredInitial);
        InterviewQuestionResponse followUp = sut.persistGeneratedFollowUpQuestion(
                user.getId(),
                created.sessionId(),
                generated);

        Message followUpMessage = messageRepository.findBySession_IdAndRoleAndTurn(
                        created.sessionId(),
                        MessageRole.QUESTION,
                        4)
                .orElseThrow();
        assertThat(followUp.questionId()).isEqualTo(4L);
        assertThat(followUp.question()).isNotBlank();
        assertThat(followUpMessage.getContent()).isEqualTo(followUp.question());
        assertThat(questionRepository.findById(followUpMessage.getId()).orElseThrow().getExpectedAnswer())
                .contains("피드백");
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

    /** Mock LLM 꼬리질문 생성에 전달할 최초 3문항 문맥을 저장소에서 조립한다. */
    private List<QuestionAnswerPair> initialPairs(Long sessionId) {
        return java.util.stream.IntStream.rangeClosed(1, 3)
                .mapToObj(turn -> {
                    Message questionMessage = messageRepository.findBySession_IdAndRoleAndTurn(
                                    sessionId, MessageRole.QUESTION, turn)
                            .orElseThrow();
                    Message answerMessage = messageRepository.findBySession_IdAndRoleAndTurn(
                                    sessionId, MessageRole.ANSWER, turn)
                            .orElseThrow();
                    String expectedAnswer = questionRepository.findById(questionMessage.getId())
                            .orElseThrow()
                            .getExpectedAnswer();
                    return new QuestionAnswerPair(
                            turn,
                            questionMessage.getContent(),
                            answerMessage.getContent(),
                            expectedAnswer);
                })
                .toList();
    }
}
