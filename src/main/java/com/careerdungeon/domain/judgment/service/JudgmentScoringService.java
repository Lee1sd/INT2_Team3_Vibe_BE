package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.judgment.llm.dto.RawEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawQuestionEvaluation;
import com.careerdungeon.domain.judgment.llm.dto.RubricScores;
import com.careerdungeon.domain.judgment.model.JudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** LLM 원시값을 검증하고 FR-04 루브릭으로 서버 확정 점수로 변환한다. */
@Service
public class JudgmentScoringService {

    private static final int PASSING_SCORE = 80;
    private static final int MAX_TOTAL_SCORE = 100;

    private final WeakestQuestionSelector weakestQuestionSelector;

    /**
     * 최저점 동점 정책을 외부에서 주입받아 운영은 랜덤, 테스트는 결정적으로 실행할 수 있게 한다.
     */
    public JudgmentScoringService(WeakestQuestionSelector weakestQuestionSelector) {
        this.weakestQuestionSelector = weakestQuestionSelector;
    }

    /**
     * LLM 원시 응답을 검증하고 서버가 신뢰할 수 있는 최종 채점 값으로 변환한다.
     *
     * @param rawResponse LLM 또는 Mock이 반환한 원시 평가 응답
     * @return 항목별 clamp와 서버 재계산이 끝난 확정 평가
     */
    public JudgmentEvaluation score(RawEvaluationResponse rawResponse) {
        validateTopLevel(rawResponse);

        List<QuestionScore> scores = new ArrayList<>();
        Set<Integer> seenQuestionIds = new HashSet<>();
        for (RawQuestionEvaluation evaluation : rawResponse.evaluations()) {
            validateQuestion(evaluation, seenQuestionIds);
            // LLM이 보고한 score는 신뢰하지 않고 5개 항목을 개별 보정한 합계로 덮어쓴다.
            scores.add(new QuestionScore(
                    evaluation.questionId(),
                    sumClampedRubric(evaluation.rubricScores()),
                    evaluation.feedback()));
        }

        // 총점·최저점·합격 여부 역시 LLM 파생값 대신 서버 확정 문항 점수로 다시 계산한다.
        int totalScore = clamp(scores.stream().mapToInt(QuestionScore::score).sum(), 0, MAX_TOTAL_SCORE);
        int minimum = scores.stream().mapToInt(QuestionScore::score).min().orElseThrow();
        List<Integer> weakestCandidates = scores.stream()
                .filter(score -> score.score() == minimum)
                .map(QuestionScore::questionId)
                .toList();
        int weakestQuestionId = weakestQuestionSelector.select(weakestCandidates);

        return new JudgmentEvaluation(
                scores,
                totalScore,
                weakestQuestionId,
                totalScore >= PASSING_SCORE,
                rawResponse.overallFeedback());
    }

    /**
     * 원시 응답의 필수 상위 필드가 모두 존재하는지 확인한다.
     */
    private void validateTopLevel(RawEvaluationResponse response) {
        if (response == null) {
            throw schemaError("평가 응답이 null입니다.");
        }
        if (response.evaluations() == null || response.evaluations().isEmpty()) {
            throw schemaError("evaluations 필드가 누락되었거나 비어 있습니다.");
        }
        if (response.totalScore() == null || response.weakestQuestionId() == null || response.passed() == null) {
            throw schemaError("totalScore, weakestQuestionId, passed는 필수 필드입니다.");
        }
        if (response.overallFeedback() == null || response.overallFeedback().isBlank()) {
            throw schemaError("overallFeedback은 필수 필드입니다.");
        }
    }

    /**
     * 문항 항목의 null, 식별자 중복, 점수·피드백·루브릭 누락을 검증한다.
     */
    private void validateQuestion(RawQuestionEvaluation evaluation, Set<Integer> seenQuestionIds) {
        if (evaluation == null) {
            throw schemaError("evaluations에 null 항목이 있습니다.");
        }
        if (evaluation.questionId() <= 0 || !seenQuestionIds.add(evaluation.questionId())) {
            throw schemaError("questionId는 양수이며 중복될 수 없습니다: " + evaluation.questionId());
        }
        if (evaluation.score() == null) {
            throw schemaError("questionId=" + evaluation.questionId() + "의 score가 누락되었습니다.");
        }
        if (evaluation.feedback() == null || evaluation.feedback().isBlank()) {
            throw schemaError("questionId=" + evaluation.questionId() + "의 feedback이 누락되었습니다.");
        }
        validateRubric(evaluation.questionId(), evaluation.rubricScores());
    }

    /**
     * 5개 루브릭 숫자 중 하나라도 JSON에서 누락됐는지 boxed 값으로 검사한다.
     */
    private void validateRubric(int questionId, RubricScores scores) {
        if (scores == null
                || scores.technicalAccuracy() == null
                || scores.coreCoverage() == null
                || scores.reasoning() == null
                || scores.specificity() == null
                || scores.tradeOffsAndExceptions() == null) {
            throw schemaError("questionId=" + questionId + "의 5개 rubricScores 중 누락된 필드가 있습니다.");
        }
    }

    /**
     * 각 루브릭을 고유 배점 범위로 clamp한 뒤 합산해 문항 점수 0~25를 만든다.
     */
    private int sumClampedRubric(RubricScores scores) {
        return clamp(scores.technicalAccuracy(), 0, 10)
                + clamp(scores.coreCoverage(), 0, 5)
                + clamp(scores.reasoning(), 0, 4)
                + clamp(scores.specificity(), 0, 3)
                + clamp(scores.tradeOffsAndExceptions(), 0, 3);
    }

    /**
     * 주어진 값을 최소값과 최대값 사이로 제한한다.
     */
    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * 공통 LLM 예외 처리기가 인식하는 스키마 검증 예외를 생성한다.
     */
    private LlmSchemaValidationException schemaError(String message) {
        return new LlmSchemaValidationException(message);
    }
}
