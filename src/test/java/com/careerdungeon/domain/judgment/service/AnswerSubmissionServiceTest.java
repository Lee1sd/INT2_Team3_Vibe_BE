package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.judgment.entity.AnswerScore;
import com.careerdungeon.domain.judgment.exception.AnswerSubmissionException;
import com.careerdungeon.domain.judgment.llm.LlmEvaluationResponseAdapter;
import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.repository.AnswerScoreRepository;
import com.careerdungeon.domain.judgment.repository.JudgmentResultRepository;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.progress.service.StageProgressionService;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Interview가 전달한 LLM 원시 평가값부터 Judgment가 처리하는 서비스 경계를 검증한다. */
@ExtendWith(MockitoExtension.class)
class AnswerSubmissionServiceTest {

    @Mock
    AnswerScoreRepository answerScoreRepository;

    @Mock
    JudgmentResultRepository judgmentResultRepository;

    @Mock
    LlmEvaluationResponseAdapter evaluationResponseAdapter;

    @Mock
    JudgmentScoringService judgmentScoringService;

    @Mock
    StageProgressionService stageProgressionService;

    AnswerSubmissionService sut;

    /** 원시 평가 어댑터와 서버 채점기를 조합한 테스트 대상을 만든다. */
    @BeforeEach
    void setUp() {
        sut = new AnswerSubmissionService(
                answerScoreRepository,
                judgmentResultRepository,
                evaluationResponseAdapter,
                judgmentScoringService,
                stageProgressionService);
    }

    /** 최초 LLM 원시 응답을 Judgment 원시 모델로 바꾼 뒤 서버 채점기에 전달하는지 확인한다. */
    @Test
    @DisplayName("최초 원시 평가값을 어댑팅해 서버 확정 채점 결과를 반환한다")
    void scoresInitialRawEvaluation() {
        InitialEvaluationResponse rawResponse = mock(InitialEvaluationResponse.class);
        RawInitialEvaluationResponse adapted = mock(RawInitialEvaluationResponse.class);
        InitialJudgmentEvaluation expected = mock(InitialJudgmentEvaluation.class);
        when(evaluationResponseAdapter.toRawInitial(rawResponse)).thenReturn(adapted);
        when(judgmentScoringService.scoreInitial(adapted)).thenReturn(expected);

        InitialJudgmentEvaluation actual = sut.scoreInitial(rawResponse);

        assertThat(actual).isSameAs(expected);
        verify(evaluationResponseAdapter).toRawInitial(rawResponse);
        verify(judgmentScoringService).scoreInitial(adapted);
    }

    /** 저장된 최초 점수와 최종 LLM 원시 응답만 서버 최종 채점기에 전달하는지 확인한다. */
    @Test
    @DisplayName("최종 원시 평가값은 저장된 최초 확정 점수와 합산한다")
    void scoresFinalRawEvaluationWithStoredInitialScores() {
        InitialJudgmentEvaluation storedInitial = mock(InitialJudgmentEvaluation.class);
        FinalEvaluationResponse rawResponse = mock(FinalEvaluationResponse.class);
        RawFinalEvaluationResponse adapted = mock(RawFinalEvaluationResponse.class);
        FinalJudgmentEvaluation expected = mock(FinalJudgmentEvaluation.class);
        when(evaluationResponseAdapter.toRawFinal(rawResponse)).thenReturn(adapted);
        when(judgmentScoringService.scoreFinal(storedInitial, adapted)).thenReturn(expected);

        FinalJudgmentEvaluation actual = sut.scoreFinal(storedInitial, rawResponse);

        assertThat(actual).isSameAs(expected);
        verify(evaluationResponseAdapter).toRawFinal(rawResponse);
        verify(judgmentScoringService).scoreFinal(storedInitial, adapted);
    }

    /** 영속화가 Interview 반영 트랜잭션 밖에서 단독 실행되지 않도록 계약을 고정한다. */
    @Test
    @DisplayName("점수와 판정 영속화는 호출자의 기존 트랜잭션에 반드시 참여한다")
    void persistenceRequiresCallerTransaction() throws NoSuchMethodException {
        Method persistInitial = AnswerSubmissionService.class.getMethod(
                "persistInitialScores", InterviewSession.class, InitialJudgmentEvaluation.class);
        Method persistFinal = AnswerSubmissionService.class.getMethod(
                "persistFinalResult", InterviewSession.class, FinalJudgmentEvaluation.class);

        assertThat(persistInitial.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
        assertThat(persistFinal.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
    }

    /** 일부 turn만 포함한 최초 결과가 저장돼 세션이 복구 불가능해지는 상황을 차단한다. */
    @Test
    @DisplayName("최초 영속화는 turn 1,2,3의 완전한 확정 평가만 허용한다")
    void rejectsIncompleteInitialEvaluationBeforePersistence() {
        InterviewSession session = mock(InterviewSession.class);
        InitialJudgmentEvaluation incomplete = new InitialJudgmentEvaluation(
                List.of(new QuestionScore(1, 20, "피드백1")),
                20,
                1,
                false);

        assertThatThrownBy(() -> sut.persistInitialScores(session, incomplete))
                .isInstanceOfSatisfying(AnswerSubmissionException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("JUDGMENT_INITIAL_EVALUATION_INVALID"));

        verifyNoInteractions(answerScoreRepository);
    }

    /** 검증된 최초 평가 세 문항이 점수 엔티티로 변환돼 한 번에 저장되는지 확인한다. */
    @Test
    @DisplayName("완전한 최초 확정 평가는 turn 1,2,3 점수로 저장한다")
    void persistsCompleteInitialEvaluation() {
        InterviewSession session = mock(InterviewSession.class);

        sut.persistInitialScores(session, initialEvaluation(20, 20, 20));

        verify(answerScoreRepository).saveAll(anyList());
    }

    /** 다른 세션의 최초 점수를 섞은 최종 결과가 판정과 해금으로 이어지지 않도록 방어한다. */
    @Test
    @DisplayName("최종 평가의 최초 점수가 현재 세션 저장값과 다르면 반영하지 않는다")
    void rejectsFinalEvaluationFromDifferentInitialScores() {
        InterviewSession session = mock(InterviewSession.class);
        when(session.getId()).thenReturn(1L);
        List<AnswerScore> storedScores = List.of(
                storedScore(1, 20, "피드백1"),
                storedScore(2, 20, "피드백2"),
                storedScore(3, 20, "피드백3"));
        when(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(1L))
                .thenReturn(storedScores);
        FinalJudgmentEvaluation mismatched = finalEvaluation(25, 25, 25, 5);

        assertThatThrownBy(() -> sut.persistFinalResult(session, mismatched))
                .isInstanceOfSatisfying(AnswerSubmissionException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("JUDGMENT_INITIAL_SCORE_CHANGED"));

        verify(answerScoreRepository, never()).save(any(AnswerScore.class));
        verifyNoInteractions(judgmentResultRepository, stageProgressionService);
    }

    /** 현재 세션의 최초 점수와 일치하는 최종 결과만 판정·진행도 반영으로 연결한다. */
    @Test
    @DisplayName("세션의 최초 점수와 일치하는 최종 평가는 turn 4와 판정을 함께 반영한다")
    void persistsFinalEvaluationMatchingStoredInitialScores() {
        InterviewSession session = session(1L, 10L, 1);
        List<AnswerScore> storedScores = List.of(
                storedScore(1, 25, "피드백1"),
                storedScore(2, 25, "피드백2"),
                storedScore(3, 25, "피드백3"));
        when(answerScoreRepository.findAllBySession_IdOrderByTurnAsc(1L))
                .thenReturn(storedScores);

        sut.persistFinalResult(session, finalEvaluation(25, 25, 25, 5));

        verify(answerScoreRepository).save(any(AnswerScore.class));
        verify(judgmentResultRepository).save(any());
        verify(stageProgressionService).applyFinalScore(10L, 1, 80);
    }

    /** 테스트에서 사용할 완전한 최초 서버 확정 평가를 만든다. */
    private InitialJudgmentEvaluation initialEvaluation(int first, int second, int third) {
        List<QuestionScore> scores = List.of(
                new QuestionScore(1, first, "피드백1"),
                new QuestionScore(2, second, "피드백2"),
                new QuestionScore(3, third, "피드백3"));
        int totalScore = first + second + third;
        int minimum = Math.min(first, Math.min(second, third));
        int weakestQuestionId = scores.stream()
                .filter(score -> score.score() == minimum)
                .map(QuestionScore::questionId)
                .findFirst()
                .orElseThrow();
        return new InitialJudgmentEvaluation(scores, totalScore, weakestQuestionId, false);
    }

    /** 테스트에서 사용할 네 문항 서버 확정 최종 평가를 만든다. */
    private FinalJudgmentEvaluation finalEvaluation(int first, int second, int third, int followUp) {
        int totalScore = first + second + third + followUp;
        return new FinalJudgmentEvaluation(
                List.of(
                        new QuestionScore(1, first, "피드백1"),
                        new QuestionScore(2, second, "피드백2"),
                        new QuestionScore(3, third, "피드백3"),
                        new QuestionScore(4, followUp, "피드백4")),
                totalScore,
                totalScore >= 80,
                "종합 피드백");
    }

    /** 저장소에서 복원되는 문항 점수 Mock을 만든다. */
    private AnswerScore storedScore(int turn, int score, String feedback) {
        AnswerScore stored = mock(AnswerScore.class);
        when(stored.getTurn()).thenReturn(turn);
        when(stored.getScore()).thenReturn(score);
        when(stored.getFeedback()).thenReturn(feedback);
        return stored;
    }

    /** 최종 판정 반영에 필요한 세션·사용자·페르소나 식별자를 가진 Mock을 만든다. */
    private InterviewSession session(long sessionId, long userId, int level) {
        InterviewSession session = mock(InterviewSession.class);
        PersonaConfig personaConfig = mock(PersonaConfig.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getUserId()).thenReturn(userId);
        when(session.getPersonaConfig()).thenReturn(personaConfig);
        when(personaConfig.getLevel()).thenReturn(level);
        return session;
    }
}
