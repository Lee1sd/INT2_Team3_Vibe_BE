package com.careerdungeon.domain.judgment.llm.mock;

import com.careerdungeon.domain.judgment.llm.EvaluationLlmClient;
import com.careerdungeon.domain.judgment.llm.dto.EvaluationRequest;
import com.careerdungeon.domain.judgment.llm.dto.QuestionAnswerPair;
import com.careerdungeon.domain.judgment.llm.dto.RawEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawQuestionEvaluation;
import com.careerdungeon.domain.judgment.llm.dto.RubricScores;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 외부 호출 없이 모범답변과 사용자 답변을 비교하는 결정적 채점 Mock.
 *
 * <p>실제 의미 정확성을 판별하는 모델이 아니라 개발·테스트용 근사치다. Claude 구현으로 교체해도
 * 5개 루브릭 응답 계약과 서버 측 재계산은 유지된다.
 */
@Component
@ConditionalOnProperty(name = "llm.evaluation.mode", havingValue = "mock", matchIfMissing = true)
public class MockEvaluationLlmClient implements EvaluationLlmClient {

    private static final Pattern NUMBER_PATTERN = Pattern.compile(".*\\d.*", Pattern.DOTALL);
    private static final Set<String> STOP_WORDS = Set.of(
            "그리고", "하지만", "대한", "통해", "사용", "합니다", "있습니다", "the", "and", "for", "with");
    private static final List<String> REASONING_SIGNALS = List.of(
            "때문", "따라서", "이유", "근거", "결과", "because", "therefore", "so that");
    private static final List<String> SPECIFICITY_SIGNALS = List.of(
            "구현", "프로젝트", "운영", "테스트", "로그", "지표", "ms", "%", "api", "db", "코드");
    private static final List<String> TRADE_OFF_SIGNALS = List.of(
            "반면", "단점", "대안", "예외", "장애", "트레이드오프", "trade-off", "however", "fallback");

    @Override
    public RawEvaluationResponse evaluate(EvaluationRequest request) {
        validateRequest(request);
        List<RawQuestionEvaluation> evaluations = request.questionAnswerPairs().stream()
                .map(pair -> evaluateQuestion(pair, request.userName()))
                .toList();
        int totalScore = evaluations.stream().mapToInt(RawQuestionEvaluation::score).sum();
        int weakestQuestionId = evaluations.stream()
                .min(java.util.Comparator.comparingInt(RawQuestionEvaluation::score))
                .orElseThrow()
                .questionId();
        String name = isBlank(request.userName()) ? "지원자" : request.userName().trim();
        return new RawEvaluationResponse(
                evaluations,
                totalScore,
                weakestQuestionId,
                totalScore >= 80,
                name + "님의 답변을 5개 루브릭 기준으로 평가했습니다.");
    }

    private RawQuestionEvaluation evaluateQuestion(QuestionAnswerPair pair, String userName) {
        String answer = normalize(pair.userAnswer());
        Set<String> expectedTokens = tokenize(pair.expectedAnswer());
        Set<String> answerTokens = tokenize(answer);
        double coverage = coverage(expectedTokens, answerTokens);

        RubricScores rubricScores;
        if (answer.isBlank()) {
            rubricScores = new RubricScores(0, 0, 0, 0, 0);
        } else {
            rubricScores = new RubricScores(
                    technicalAccuracy(coverage),
                    coreCoverage(coverage),
                    signalScore(answer, REASONING_SIGNALS, 4, answerTokens.size() >= 8 ? 1 : 0),
                    specificityScore(answer),
                    signalScore(answer, TRADE_OFF_SIGNALS, 3, 0));
        }
        int score = rubricScores.technicalAccuracy()
                + rubricScores.coreCoverage()
                + rubricScores.reasoning()
                + rubricScores.specificity()
                + rubricScores.tradeOffsAndExceptions();
        return new RawQuestionEvaluation(
                pair.questionId(),
                score,
                rubricScores,
                feedback(userName, rubricScores));
    }

    private int technicalAccuracy(double coverage) {
        if (coverage >= 0.8) return 10;
        if (coverage >= 0.6) return 8;
        if (coverage >= 0.4) return 6;
        if (coverage >= 0.2) return 4;
        if (coverage > 0) return 2;
        return 1;
    }

    private int coreCoverage(double coverage) {
        if (coverage >= 0.8) return 5;
        if (coverage >= 0.6) return 4;
        if (coverage >= 0.4) return 3;
        if (coverage >= 0.2) return 2;
        if (coverage > 0) return 1;
        return 0;
    }

    private int specificityScore(String answer) {
        int score = signalScore(answer, SPECIFICITY_SIGNALS, 3, 0);
        if (NUMBER_PATTERN.matcher(answer).matches()) {
            score++;
        }
        return Math.min(3, score);
    }

    private int signalScore(String answer, List<String> signals, int maximum, int baseline) {
        long matches = signals.stream().filter(answer::contains).count();
        return Math.min(maximum, baseline + (int) matches);
    }

    private double coverage(Set<String> expectedTokens, Set<String> answerTokens) {
        if (expectedTokens.isEmpty() || answerTokens.isEmpty()) {
            return 0;
        }
        Set<String> overlap = new HashSet<>(expectedTokens);
        overlap.retainAll(answerTokens);
        return (double) overlap.size() / expectedTokens.size();
    }

    private Set<String> tokenize(String value) {
        if (isBlank(value)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(normalize(value).split("[^\\p{L}\\p{N}]+"))
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .forEach(result::add);
        return result;
    }

    private String feedback(String userName, RubricScores scores) {
        String name = isBlank(userName) ? "지원자" : userName.trim();
        if (scores.technicalAccuracy() <= 4 || scores.coreCoverage() <= 2) {
            return name + "님, 모범답변의 핵심 개념을 더 직접적으로 포함해 주세요.";
        }
        if (scores.reasoning() <= 1 || scores.tradeOffsAndExceptions() == 0) {
            return name + "님, 선택 근거와 트레이드오프 또는 예외 상황을 보강해 주세요.";
        }
        return name + "님, 핵심 개념과 판단 근거를 구체적으로 설명했습니다.";
    }

    private void validateRequest(EvaluationRequest request) {
        if (request == null || request.questionAnswerPairs().isEmpty()) {
            throw new IllegalArgumentException("채점할 질문-답변 쌍이 필요합니다.");
        }
        Set<Integer> ids = new HashSet<>();
        for (QuestionAnswerPair pair : request.questionAnswerPairs()) {
            if (pair == null || pair.questionId() <= 0 || !ids.add(pair.questionId())) {
                throw new IllegalArgumentException("질문 ID는 양수이며 중복될 수 없습니다.");
            }
            if (isBlank(pair.questionText()) || isBlank(pair.expectedAnswer())) {
                throw new IllegalArgumentException("질문과 모범답변은 필수입니다.");
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
