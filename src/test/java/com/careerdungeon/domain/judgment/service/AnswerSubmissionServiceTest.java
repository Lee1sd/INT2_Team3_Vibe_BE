package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.judgment.llm.LlmEvaluationResponseAdapter;
import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.repository.AnswerScoreRepository;
import com.careerdungeon.domain.judgment.repository.JudgmentResultRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
}
