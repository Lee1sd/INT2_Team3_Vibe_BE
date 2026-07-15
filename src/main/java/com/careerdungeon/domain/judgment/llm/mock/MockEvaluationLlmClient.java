package com.careerdungeon.domain.judgment.llm.mock;

import com.careerdungeon.domain.judgment.llm.EvaluationLlmClient;
import com.careerdungeon.domain.judgment.llm.dto.EvaluationRequest;
import com.careerdungeon.domain.judgment.llm.dto.QuestionAnswerPair;
import com.careerdungeon.domain.judgment.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
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
    private static final Set<Integer> INITIAL_QUESTION_IDS = Set.of(1, 2, 3);
    private static final Set<Integer> FINAL_QUESTION_IDS = Set.of(4);

    /**
     * 최초 세 문항을 독립적으로 평가하고 꼬리질문 생성에 필요한 최저점 문항을 보고한다.
     *
     * @param request 질문·답변·모범답변을 포함한 채점 요청
     * @return 5개 루브릭과 최초 채점 파생값을 포함한 원시 평가 응답
     */
    @Override
    public RawInitialEvaluationResponse evaluateInitial(EvaluationRequest request) {
        validateRequest(request, INITIAL_QUESTION_IDS, "최초 채점");
        if (!request.previousEvaluations().isEmpty()) {
            throw new IllegalArgumentException("최초 채점에는 이전 평가 컨텍스트를 전달할 수 없습니다.");
        }
        List<RawQuestionEvaluation> evaluations = evaluateQuestions(request);
        // Mock도 실제 LLM 스키마처럼 파생값을 채우지만, 최종 신뢰 경계는 JudgmentScoringService다.
        int totalScore = evaluations.stream().mapToInt(RawQuestionEvaluation::score).sum();
        int weakestQuestionId = evaluations.stream()
                .min(java.util.Comparator.comparingInt(RawQuestionEvaluation::score))
                .orElseThrow()
                .questionId();
        return new RawInitialEvaluationResponse(
                evaluations,
                totalScore,
                weakestQuestionId,
                totalScore >= 80);
    }

    /**
     * 꼬리질문 한 문항만 평가해 최종 합산에 사용할 원시 응답을 생성한다.
     *
     * @param request questionId 4의 질문·답변·모범답변
     * @return 꼬리질문 평가와 종합 피드백을 포함한 최종 원시 응답
     */
    @Override
    public RawFinalEvaluationResponse evaluateFinal(EvaluationRequest request) {
        validateRequest(request, FINAL_QUESTION_IDS, "최종 채점");
        validatePreviousEvaluations(request.previousEvaluations());
        List<RawQuestionEvaluation> evaluations = evaluateQuestions(request);
        int totalScore = evaluations.stream().mapToInt(RawQuestionEvaluation::score).sum();
        String name = isBlank(request.userName()) ? "지원자" : request.userName().trim();
        PreviousEvaluationContext weakest = request.previousEvaluations().stream()
                .min(java.util.Comparator.comparingInt(PreviousEvaluationContext::score))
                .orElseThrow();
        return new RawFinalEvaluationResponse(
                evaluations,
                totalScore,
                totalScore >= 80,
                name + "님의 전체 면접에서 questionId=" + weakest.questionId()
                        + " 답변은 " + weakest.feedback()
                        + " 꼬리질문 답변까지 반영해 보완 정도를 종합했습니다.");
    }

    /** 요청에 포함된 모든 질문·답변 쌍을 같은 루브릭 흐름으로 평가한다. */
    private List<RawQuestionEvaluation> evaluateQuestions(EvaluationRequest request) {
        return request.questionAnswerPairs().stream()
                .map(pair -> evaluateQuestion(pair, request.userName()))
                .toList();
    }

    /**
     * 모범답변 핵심 토큰 충족도와 답변 표현 신호를 조합해 한 문항의 5개 루브릭을 계산한다.
     *
     * @param pair 평가할 질문·답변·모범답변 쌍
     * @param userName 피드백 개인화에 사용할 사용자 이름
     * @return 문항별 원시 평가
     */
    private RawQuestionEvaluation evaluateQuestion(QuestionAnswerPair pair, String userName) {
        String answer = normalize(pair.userAnswer());
        Set<String> expectedTokens = tokenize(pair.expectedAnswer());
        Set<String> answerTokens = tokenize(answer);
        double coverage = coverage(expectedTokens, answerTokens);

        RubricScores rubricScores;
        if (answer.isBlank()) {
            // 무응답은 표현 신호나 토큰 우연 일치 여부와 무관하게 전 항목 0점으로 고정한다.
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

    /**
     * 모범답변 토큰 충족 비율을 기술적 정확성의 6단계 점수 구간으로 변환한다.
     */
    private int technicalAccuracy(double coverage) {
        if (coverage >= 0.8) return 10;
        if (coverage >= 0.6) return 8;
        if (coverage >= 0.4) return 6;
        if (coverage >= 0.2) return 4;
        if (coverage > 0) return 2;
        return 1;
    }

    /**
     * 모범답변 토큰 충족 비율을 핵심 내용 충족도의 0~5점 구간으로 변환한다.
     */
    private int coreCoverage(double coverage) {
        if (coverage >= 0.8) return 5;
        if (coverage >= 0.6) return 4;
        if (coverage >= 0.4) return 3;
        if (coverage >= 0.2) return 2;
        if (coverage > 0) return 1;
        return 0;
    }

    /**
     * 실무 용어와 수치 표현을 세어 구체성·실무 연계 점수를 최대 3점으로 제한한다.
     */
    private int specificityScore(String answer) {
        int score = signalScore(answer, SPECIFICITY_SIGNALS, 3, 0);
        if (NUMBER_PATTERN.matcher(answer).matches()) {
            score++;
        }
        return Math.min(3, score);
    }

    /**
     * 답변에 포함된 표현 신호 수를 세고 루브릭별 상한을 적용한다.
     */
    private int signalScore(String answer, List<String> signals, int maximum, int baseline) {
        long matches = signals.stream().filter(answer::contains).count();
        return Math.min(maximum, baseline + (int) matches);
    }

    /**
     * 모범답변 핵심 토큰 중 사용자 답변에 등장한 토큰의 비율을 계산한다.
     */
    private double coverage(Set<String> expectedTokens, Set<String> answerTokens) {
        if (expectedTokens.isEmpty() || answerTokens.isEmpty()) {
            return 0;
        }
        Set<String> overlap = new HashSet<>(expectedTokens);
        overlap.retainAll(answerTokens);
        return (double) overlap.size() / expectedTokens.size();
    }

    /**
     * 한글·영문·숫자 토큰만 남기고 일반 불용어와 한 글자 토큰을 제거한다.
     */
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

    /**
     * 낮은 루브릭을 우선해 사용자가 보완할 지점을 한글 피드백으로 생성한다.
     */
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

    /**
     * 채점 전에 단계별 문항 집합과 질문·모범답변 필수값을 검증한다.
     *
     * <p>잘못된 내부 요청은 같은 값으로 재시도해도 복구되지 않으므로 재시도 대상인
     * LlmSchemaValidationException이 아니라 IllegalArgumentException으로 즉시 실패시킨다.
     */
    private void validateRequest(EvaluationRequest request, Set<Integer> expectedIds, String phase) {
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
        if (!ids.equals(expectedIds)) {
            throw new IllegalArgumentException(
                    phase + " 문항 구성은 " + expectedIds + "여야 합니다: " + ids);
        }
    }

    /** 최종 피드백용 최초 평가 컨텍스트가 1~3 전체이며 서버 확정 범위인지 검증한다. */
    private void validatePreviousEvaluations(List<PreviousEvaluationContext> contexts) {
        if (contexts == null || contexts.size() != INITIAL_QUESTION_IDS.size()) {
            throw new IllegalArgumentException("최종 채점에는 최초 평가 컨텍스트 3건이 필요합니다.");
        }
        Set<Integer> ids = new HashSet<>();
        for (PreviousEvaluationContext context : contexts) {
            if (context == null || !ids.add(context.questionId())
                    || isBlank(context.questionText()) || isBlank(context.userAnswer())
                    || isBlank(context.feedback()) || context.score() < 0 || context.score() > 25) {
                throw new IllegalArgumentException("최초 평가 컨텍스트가 올바르지 않습니다.");
            }
        }
        if (!ids.equals(INITIAL_QUESTION_IDS)) {
            throw new IllegalArgumentException("최초 평가 컨텍스트 문항 구성은 1,2,3이어야 합니다.");
        }
    }

    /**
     * null을 빈 문자열로 바꾸고 대소문자와 앞뒤 공백을 정규화한다.
     */
    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * 문자열이 null이거나 공백만 있는지 확인한다.
     */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
