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

    public JudgmentScoringService(WeakestQuestionSelector weakestQuestionSelector) {
        this.weakestQuestionSelector = weakestQuestionSelector;
    }

    public JudgmentEvaluation score(RawEvaluationResponse rawResponse) {
        validateTopLevel(rawResponse);

        List<QuestionScore> scores = new ArrayList<>();
        Set<Integer> seenQuestionIds = new HashSet<>();
        for (RawQuestionEvaluation evaluation : rawResponse.evaluations()) {
            validateQuestion(evaluation, seenQuestionIds);
            scores.add(new QuestionScore(
                    evaluation.questionId(),
                    sumClampedRubric(evaluation.rubricScores()),
                    evaluation.feedback()));
        }

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

    private int sumClampedRubric(RubricScores scores) {
        return clamp(scores.technicalAccuracy(), 0, 10)
                + clamp(scores.coreCoverage(), 0, 5)
                + clamp(scores.reasoning(), 0, 4)
                + clamp(scores.specificity(), 0, 3)
                + clamp(scores.tradeOffsAndExceptions(), 0, 3);
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private LlmSchemaValidationException schemaError(String message) {
        return new LlmSchemaValidationException(message);
    }
}
