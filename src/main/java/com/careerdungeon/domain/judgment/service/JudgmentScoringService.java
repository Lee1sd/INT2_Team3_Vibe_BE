package com.careerdungeon.domain.judgment.service;

import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawQuestionEvaluation;
import com.careerdungeon.domain.judgment.llm.dto.RubricScores;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** LLM 원시값을 단계별로 검증하고 FR-04 루브릭으로 서버 확정 점수로 변환한다. */
@Service
public class JudgmentScoringService {

    private static final int MAXIMUM_QUESTION_SCORE = 20;
    private static final Set<Integer> INITIAL_QUESTION_IDS = Set.of(1, 2, 3, 4);
    private static final Set<Integer> FOLLOW_UP_QUESTION_IDS = Set.of(5);
    private static final Set<Integer> FINAL_REQUIRED_FEEDBACK_IDS = Set.of(5);

    private final WeakestQuestionSelector weakestQuestionSelector;

    /** 최저점 동점 정책을 주입해 운영은 랜덤, 테스트는 결정적으로 실행할 수 있게 한다. */
    public JudgmentScoringService(WeakestQuestionSelector weakestQuestionSelector) {
        this.weakestQuestionSelector = weakestQuestionSelector;
    }

    /**
     * 최초 네 문항을 검증·보정하고 꼬리질문 생성에 전달할 최저점 문항을 확정한다.
     *
     * @param rawResponse LLM 또는 Mock이 반환한 최초 원시 평가
     * @return questionId 1~4의 확정 점수와 최저점 문항
     */
    public InitialJudgmentEvaluation scoreInitial(RawInitialEvaluationResponse rawResponse) {
        validateInitialTopLevel(rawResponse);
        List<QuestionScore> scores = toScores(
                rawResponse.evaluations(), INITIAL_QUESTION_IDS, INITIAL_QUESTION_IDS);
        int totalScore = scores.stream().mapToInt(QuestionScore::score).sum();
        int weakestQuestionId = selectWeakestQuestion(scores);

        return new InitialJudgmentEvaluation(
                scores,
                totalScore,
                weakestQuestionId,
                false);
    }

    /**
     * 최초 확정 점수는 유지하고 꼬리질문 한 문항만 검증·보정해 레벨별 최종 판정을 만든다.
     *
     * @param initialEvaluation 최초 채점에서 서버가 확정한 questionId 1~4 결과와 통과 기준
     * @param rawResponse LLM 또는 Mock이 반환한 questionId 5 원시 평가
     * @return 기존 questionId 1~4와 신규 questionId 5를 합친 종합 판정
     */
    public FinalJudgmentEvaluation scoreFinal(
            InitialJudgmentEvaluation initialEvaluation,
            RawFinalEvaluationResponse rawResponse) {
        List<QuestionScore> initialScores = normalizeInitialScores(initialEvaluation);
        validateFinalTopLevel(rawResponse);
        List<QuestionScore> followUpScores = toScores(
                rawResponse.evaluations(), FOLLOW_UP_QUESTION_IDS, FINAL_REQUIRED_FEEDBACK_IDS);
        List<QuestionScore> scores = new ArrayList<>(initialScores);
        scores.addAll(followUpScores);
        int totalScore = scores.stream().mapToInt(QuestionScore::score).sum();
        int passingScore = initialEvaluation.passingScore();
        boolean passed = totalScore >= passingScore;
        String personalizedFeedback = FinalFeedbackReportPersonalizer.apply(
                rawResponse.overallFeedback(),
                totalScore,
                passingScore,
                passed);

        return new FinalJudgmentEvaluation(
                scores,
                totalScore,
                passed,
                personalizedFeedback,
                passingScore);
    }

    /** 저장 후 다시 불러온 최초 점수도 문항 구성과 범위를 재검증해 최종 합산을 방어한다. */
    private List<QuestionScore> normalizeInitialScores(InitialJudgmentEvaluation initialEvaluation) {
        if (initialEvaluation == null || initialEvaluation.evaluations() == null) {
            throw schemaError("최초 확정 평가가 누락되었습니다.");
        }

        List<QuestionScore> normalized = new ArrayList<>();
        Set<Integer> seenQuestionIds = new HashSet<>();
        for (QuestionScore score : initialEvaluation.evaluations()) {
            if (score == null || !INITIAL_QUESTION_IDS.contains(score.questionId())
                    || !seenQuestionIds.add(score.questionId())) {
                throw schemaError("최초 확정 평가 문항 구성은 1,2,3,4여야 합니다.");
            }
            normalized.add(new QuestionScore(
                    score.questionId(),
                    clamp(score.score(), 0, MAXIMUM_QUESTION_SCORE),
                    score.feedback()));
        }
        if (!seenQuestionIds.equals(INITIAL_QUESTION_IDS)) {
            throw schemaError("최초 확정 평가 문항 구성은 1,2,3,4여야 합니다: " + seenQuestionIds);
        }
        // 저장소 조회 순서와 무관하게 외부 최종 응답은 questionId 1~5 순서를 유지한다.
        normalized.sort(Comparator.comparingInt(QuestionScore::questionId));
        return List.copyOf(normalized);
    }

    /** 최초 응답에만 존재하는 파생 필드의 누락을 검증한다. */
    private void validateInitialTopLevel(RawInitialEvaluationResponse response) {
        if (response == null) {
            throw schemaError("최초 평가 응답이 null입니다.");
        }
        if (response.totalScore() == null || response.weakestQuestionId() == null || response.passed() == null) {
            throw schemaError("최초 응답의 totalScore, weakestQuestionId, passed는 필수 필드입니다.");
        }
    }

    /** 최종 응답에만 존재하는 종합 피드백과 파생 필드의 누락을 검증한다. */
    private void validateFinalTopLevel(RawFinalEvaluationResponse response) {
        if (response == null) {
            throw schemaError("최종 평가 응답이 null입니다.");
        }
        if (response.totalScore() == null || response.passed() == null) {
            throw schemaError("최종 응답의 totalScore, passed는 필수 필드입니다.");
        }
        if (response.overallFeedback() == null || response.overallFeedback().isBlank()) {
            throw schemaError("최종 응답의 overallFeedback은 필수 필드입니다.");
        }
    }

    /**
     * 단계별 문항 집합과 필수 피드백을 검증한 뒤 각 루브릭을 clamp해 문항 점수를 만든다.
     */
    private List<QuestionScore> toScores(
            List<RawQuestionEvaluation> evaluations,
            Set<Integer> expectedQuestionIds,
            Set<Integer> requiredFeedbackIds) {
        if (evaluations == null || evaluations.isEmpty()) {
            throw schemaError("evaluations 필드가 누락되었거나 비어 있습니다.");
        }

        List<QuestionScore> scores = new ArrayList<>();
        Set<Integer> seenQuestionIds = new HashSet<>();
        for (RawQuestionEvaluation evaluation : evaluations) {
            validateQuestion(evaluation, seenQuestionIds, requiredFeedbackIds);
            // LLM 보고 score는 선택 호환 필드이므로 누락 여부와 값에 관계없이 루브릭으로 덮어쓴다.
            scores.add(new QuestionScore(
                    evaluation.questionId(),
                    sumClampedRubric(evaluation.rubricScores()),
                    evaluation.feedback()));
        }
        if (!seenQuestionIds.equals(expectedQuestionIds)) {
            throw schemaError(
                    "평가 문항 구성은 " + expectedQuestionIds + "여야 합니다: " + seenQuestionIds);
        }
        return List.copyOf(scores);
    }

    /** 문항의 null, 식별자 범위·중복, 단계별 피드백, 루브릭 누락을 검증한다. */
    private void validateQuestion(
            RawQuestionEvaluation evaluation,
            Set<Integer> seenQuestionIds,
            Set<Integer> requiredFeedbackIds) {
        if (evaluation == null) {
            throw schemaError("evaluations에 null 항목이 있습니다.");
        }
        if (evaluation.questionId() < 1 || evaluation.questionId() > 5
                || !seenQuestionIds.add(evaluation.questionId())) {
            throw schemaError("questionId는 1~5이며 중복될 수 없습니다: " + evaluation.questionId());
        }
        if (requiredFeedbackIds.contains(evaluation.questionId())
                && (evaluation.feedback() == null || evaluation.feedback().isBlank())) {
            throw schemaError("questionId=" + evaluation.questionId() + "의 feedback이 누락되었습니다.");
        }
        validateRubric(evaluation.questionId(), evaluation.rubricScores());
    }

    /** 5개 루브릭 숫자 중 하나라도 JSON에서 누락됐는지 boxed 값으로 검사한다. */
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

    /** 각 루브릭을 고유 배점 범위로 clamp한 뒤 합산해 문항 점수 0~20을 만든다. */
    private int sumClampedRubric(RubricScores scores) {
        return clamp(scores.technicalAccuracy(), 0, 8)
                + clamp(scores.coreCoverage(), 0, 4)
                + clamp(scores.reasoning(), 0, 3)
                + clamp(scores.specificity(), 0, 3)
                + clamp(scores.tradeOffsAndExceptions(), 0, 2);
    }

    /** 최저점 동점 후보 전체를 선택 정책에 전달한다. */
    private int selectWeakestQuestion(List<QuestionScore> scores) {
        int minimum = scores.stream().mapToInt(QuestionScore::score).min().orElseThrow();
        List<Integer> candidates = scores.stream()
                .filter(score -> score.score() == minimum)
                .map(QuestionScore::questionId)
                .toList();
        return weakestQuestionSelector.select(candidates);
    }

    /** 주어진 값을 최소값과 최대값 사이로 제한한다. */
    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** 공통 LLM 재시도·예외 처리기가 인식하는 응답 스키마 예외를 생성한다. */
    private LlmSchemaValidationException schemaError(String message) {
        return new LlmSchemaValidationException(message);
    }
}
