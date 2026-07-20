package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.interview.repository.InterviewSessionRepository;
import com.careerdungeon.domain.judgment.llm.LlmEvaluationResponseAdapter;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.repository.AnswerScoreRepository;
import com.careerdungeon.domain.judgment.repository.JudgmentResultRepository;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.exception.BadgeNotFoundException;
import com.careerdungeon.domain.progress.repository.BadgeRepository;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.domain.progress.service.BadgeAwardService;
import com.careerdungeon.domain.progress.service.ProgressGaugeService;
import com.careerdungeon.domain.progress.service.StageProgressionService;
import com.careerdungeon.domain.resume.entity.Resume;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 최종 판정·문항 점수·진행도·뱃지가 호출자의 반영 트랜잭션에서 함께 롤백되는지 검증한다. */
@DataJpaTest
@Import({
        AnswerSubmissionService.class,
        LlmEvaluationResponseAdapter.class,
        JudgmentScoringService.class,
        RandomWeakestQuestionSelector.class,
        StageProgressionService.class,
        ProgressGaugeService.class,
        BadgeAwardService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AnswerSubmissionPersistenceTransactionTest {

    @Autowired
    AnswerSubmissionService answerSubmissionService;

    @Autowired
    AnswerScoreRepository answerScoreRepository;

    @Autowired
    JudgmentResultRepository judgmentResultRepository;

    @Autowired
    InterviewSessionRepository interviewSessionRepository;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    BadgeRepository badgeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ResumeRepository resumeRepository;

    @Autowired
    PersonaConfigRepository personaConfigRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate transactionTemplate;
    TestFixture fixture;

    /** 커밋된 최초 점수 세 건과 뱃지 기준 데이터가 없는 진행도 상태를 준비한다. */
    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        fixture = transactionTemplate.execute(status -> {
            // 운영 seed가 있어도 이 테스트는 Stage2 누락 실패를 재현해야 하므로 기준 데이터를 제거한다.
            badgeRepository.delete(badgeRepository.findByStage(2).orElseThrow());
            badgeRepository.flush();
            return createFixture();
        });
    }

    /** 뱃지 지급 실패 시 앞서 저장한 turn 4와 최종 판정·진행도까지 함께 롤백되는지 확인한다. */
    @Test
    @DisplayName("최종 진행도 반영 실패는 turn 4 점수와 판정까지 함께 롤백한다")
    void finalPersistenceRollsBackWhenProgressionFails() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            InterviewSession session = interviewSessionRepository.findById(fixture.sessionId()).orElseThrow();
            answerSubmissionService.persistFinalResult(session, finalEvaluation());
        })).isInstanceOf(BadgeNotFoundException.class);

        assertThat(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(fixture.sessionId()))
                .hasSize(3)
                .extracting(score -> score.getTurn())
                .containsExactly(1, 2, 3);
        assertThat(judgmentResultRepository.existsBySession_Id(fixture.sessionId())).isFalse();
        UserUnlockStatus progress = userUnlockStatusRepository.findById(fixture.userId()).orElseThrow();
        assertThat(progress.getUnlockedLevel()).isEqualTo(1);
        assertThat(progress.getProgressGauge()).isZero();
    }

    /** 사용자·진행도·세션과 최초 turn 1~3 확정 점수를 하나의 준비 트랜잭션에 저장한다. */
    private TestFixture createFixture() {
        String identifier = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(new User(
                "judgment-transaction-" + identifier,
                identifier + "@example.com",
                "판정 트랜잭션 사용자"));
        userUnlockStatusRepository.saveAndFlush(UserUnlockStatus.initialFor(user));

        Resume resume = new Resume(
                user.getId(),
                ResumeType.RESUME,
                "resumes/judgment-transaction.pdf",
                "hash-" + identifier);
        resume.markDone("트랜잭션 검증용 이력서", Instant.now().plusSeconds(3600));
        resumeRepository.saveAndFlush(resume);
        PersonaConfig persona = personaConfigRepository.saveAndFlush(
                new PersonaConfig(1, PersonaTone.STRICT));
        InterviewSession session = interviewSessionRepository.saveAndFlush(
                new InterviewSession(user, resume, persona, "DB"));
        answerSubmissionService.persistInitialScores(session, initialEvaluation());
        return new TestFixture(user.getId(), session.getId());
    }

    /** 최초 turn 1~3의 서버 확정 점수와 피드백을 만든다. */
    private InitialJudgmentEvaluation initialEvaluation() {
        return new InitialJudgmentEvaluation(
                List.of(
                        new QuestionScore(1, 25, "피드백1"),
                        new QuestionScore(2, 25, "피드백2"),
                        new QuestionScore(3, 25, "피드백3")),
                75,
                1,
                false);
    }

    /** 합격 경계인 80점의 네 문항 최종 확정 평가를 만든다. */
    private FinalJudgmentEvaluation finalEvaluation() {
        return new FinalJudgmentEvaluation(
                List.of(
                        new QuestionScore(1, 25, "피드백1"),
                        new QuestionScore(2, 25, "피드백2"),
                        new QuestionScore(3, 25, "피드백3"),
                        new QuestionScore(4, 5, "피드백4")),
                80,
                true,
                "종합 피드백");
    }

    /** 테스트에서 재조회할 사용자와 면접 세션 식별자를 묶는다. */
    private record TestFixture(long userId, long sessionId) {
    }
}
