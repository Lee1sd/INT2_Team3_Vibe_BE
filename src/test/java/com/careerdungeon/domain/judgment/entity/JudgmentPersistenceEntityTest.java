package com.careerdungeon.domain.judgment.entity;

import com.careerdungeon.domain.interview.entity.InterviewSession;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.persona.PersonaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 문항 점수와 레벨별 최종 판정의 엔티티 생성 불변식을 검증한다. */
class JudgmentPersistenceEntityTest {

    /** 문항 점수 0~20과 꼬리질문 turn 5 경계를 엔티티에서도 강제하는지 확인한다. */
    @ParameterizedTest
    @ValueSource(ints = {0, 20})
    @DisplayName("AnswerScore는 0~20점과 turn 5 꼬리질문을 저장한다")
    void answerScoreAcceptsNewBoundaries(int score) {
        AnswerScore answerScore = AnswerScore.from(
                session(1),
                new QuestionScore(5, score, "피드백"));

        assertThat(answerScore.getScore()).isEqualTo(score);
        assertThat(answerScore.isFollowUp()).isTrue();
    }

    /** 문항 점수 상·하한 이탈을 엔티티 생성 시점에 거부하는지 확인한다. */
    @ParameterizedTest
    @ValueSource(ints = {-1, 21})
    @DisplayName("AnswerScore는 0~20 범위 밖 점수를 거부한다")
    void answerScoreRejectsOutOfRangeScore(int score) {
        assertThatThrownBy(() -> AnswerScore.from(
                session(1),
                new QuestionScore(1, score, "피드백")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0~20");
    }

    /** Lv.1 60점 판정이 엔티티의 passed 값으로 그대로 보존되는지 확인한다. */
    @Test
    @DisplayName("JudgmentResult는 Lv.1 60점 이상을 합격으로 저장한다")
    void judgmentResultUsesLevelOnePassingScore() {
        JudgmentResult result = JudgmentResult.from(session(1), evaluation(60, 60));

        assertThat(result.getTotalScore()).isEqualTo(60);
        assertThat(result.isPassed()).isTrue();
    }

    /** 다른 레벨의 통과 기준으로 만든 평가가 세션에 저장되지 않도록 교차 검증한다. */
    @Test
    @DisplayName("JudgmentResult는 세션 레벨과 다른 통과 기준을 거부한다")
    void judgmentResultRejectsMismatchedLevelPolicy() {
        FinalJudgmentEvaluation levelOneEvaluation = evaluation(60, 60);

        assertThatThrownBy(() -> JudgmentResult.from(session(2), levelOneEvaluation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("레벨별 통과 기준");
    }

    /** 지정한 총점과 통과 기준을 만족하는 다섯 문항 최종 평가를 만든다. */
    private FinalJudgmentEvaluation evaluation(int totalScore, int passingScore) {
        int remaining = totalScore;
        List<QuestionScore> scores = new ArrayList<>();
        for (int turn = 1; turn <= 5; turn++) {
            int score = Math.min(20, remaining);
            remaining -= score;
            scores.add(new QuestionScore(turn, score, "피드백" + turn));
        }
        return new FinalJudgmentEvaluation(
                scores,
                totalScore,
                totalScore >= passingScore,
                "종합 피드백",
                passingScore);
    }

    /** 테스트용 세션과 페르소나 레벨을 구성한다. */
    private InterviewSession session(int level) {
        InterviewSession session = mock(InterviewSession.class);
        PersonaConfig personaConfig = mock(PersonaConfig.class);
        when(session.getPersonaConfig()).thenReturn(personaConfig);
        when(personaConfig.getLevel()).thenReturn(level);
        return session;
    }
}
