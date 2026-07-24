package com.careerdungeon.global.llm.mock;

import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.validation.PreviousEvaluationContextValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Arrays;

/**
 * 실 LLM API 호출 없이 고정 응답을 반환하는 Mock 구현체.
 * llm.mode=mock(기본값)일 때 Bean으로 등록된다 (NFR-11, llm-cost-policy.md §1).
 *
 * 합격/불합격 시나리오 전환:
 * - application-local.yml에서 {@code llm.mock.score-per-question} 조정 (허용 범위 0~20)
 * - 최종 합격 판정은 이 Mock이 아니라 최초 확정 점수와 합산하는 judgment가 담당
 * - 단위 테스트: {@link #MockLlmClient(int)} 생성자로 점수 직접 주입
 *
 * <p>최초 채점은 turn 1~4, 최종 채점은 turn 5 한 문항만 독립 평가한다.
 */
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final int TECHNICAL_ACCURACY_MAX = 8;
    private static final int CORE_COVERAGE_MAX = 4;
    private static final int REASONING_MAX = 3;
    private static final int SPECIFICITY_MAX = 3;
    private static final int TRADE_OFFS_AND_EXCEPTIONS_MAX = 2;
    private static final int RUBRIC_TOTAL_MAX = 20;
    private static final int[] RUBRIC_MAX_SCORES = {
            TECHNICAL_ACCURACY_MAX,
            CORE_COVERAGE_MAX,
            REASONING_MAX,
            SPECIFICITY_MAX,
            TRADE_OFFS_AND_EXCEPTIONS_MAX
    };
    // 루브릭 배점 변경 시 총점 상수와 어긋나는 설정을 애플리케이션 시작 전에 차단한다.
    static {
        int rubricMaxSum = Arrays.stream(RUBRIC_MAX_SCORES).sum();
        if (rubricMaxSum != RUBRIC_TOTAL_MAX) {
            throw new IllegalStateException(
                    "Mock 루브릭 최대값 합계가 문항 총점과 일치해야 합니다: "
                            + rubricMaxSum + " != " + RUBRIC_TOTAL_MAX);
        }
    }

    private final int scorePerQuestion;

    public MockLlmClient(@Value("${llm.mock.score-per-question:18}") int scorePerQuestion) {
        if (scorePerQuestion < 0 || scorePerQuestion > RUBRIC_TOTAL_MAX) {
            throw new IllegalArgumentException("Mock 문항 점수는 0~20이어야 합니다: " + scorePerQuestion);
        }
        this.scorePerQuestion = scorePerQuestion;
    }

    @Override
    public QuestionGenerationResponse generateQuestions(QuestionGenerationRequest request) {
        String name = request.userName();
        return new QuestionGenerationResponse(List.of(
                new GeneratedQuestion(1,
                        name + "님, Java의 GC 동작 방식을 설명해 주세요.",
                        "Stop-the-world pause, 세대별 GC(Young/Old), Minor GC와 Major GC 차이 설명"),
                new GeneratedQuestion(2,
                        "데이터베이스 인덱스를 언제 사용하면 좋은지 설명해 주세요.",
                        "카디널리티가 높은 컬럼, WHERE·JOIN·ORDER BY 절 빈번 사용 컬럼에 적용"),
                new GeneratedQuestion(3,
                        "REST API 설계 원칙을 설명해 주세요.",
                        "Stateless, 자원 중심 URI, HTTP 메서드 의미 준수, 적절한 상태코드 반환"),
                new GeneratedQuestion(4,
                        "트랜잭션 격리 수준을 선택하는 기준을 설명해 주세요.",
                        "READ COMMITTED/REPEATABLE READ 등 격리 수준별 이상 현상(dirty/non-repeatable/phantom read)과 동시성·성능 트레이드오프 설명")
        ));
    }

    @Override
    public InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request) {
        List<QuestionEvaluation> evaluations = request.questionAnswerPairs().stream()
                .map(pair -> buildEvaluation(pair, request.userName()))
                .toList();
        int weakestQuestionId = findWeakestTurn(evaluations);
        int totalScore = evaluations.stream().mapToInt(QuestionEvaluation::score).sum();
        return new InitialEvaluationResponse(evaluations, totalScore, weakestQuestionId, false);
    }

    @Override
    public FollowUpGenerationResponse generateFollowUp(
            int weakestQuestionId,
            String questionText,
            String userAnswer,
            String feedback) {
        return new FollowUpGenerationResponse(
                "방금 답변에서 부족했던 부분을 보완해, " + weakestQuestionId
                        + "번 질문의 핵심 판단 기준을 더 구체적으로 설명해 주세요.",
                "기존 질문의 핵심 개념을 먼저 짚고, 답변에서 빠진 판단 근거와 실무 적용 포인트를 "
                        + "구체적인 예시와 함께 설명한다. 피드백에서 지적된 누락 내용을 직접 보완해야 한다.");
    }

    @Override
    public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request) {
        List<QuestionEvaluation> evaluations = request.questionAnswerPairs().stream()
                .map(pair -> buildEvaluation(pair, request.userName()))
                .toList();
        int totalScore = evaluations.stream().mapToInt(QuestionEvaluation::score).sum();
        List<PreviousEvaluationContext> previousEvaluations = request.previousEvaluations();
        PreviousEvaluationContextValidator.validate(previousEvaluations);
        PreviousEvaluationContext weakest = previousEvaluations.stream()
                .min(java.util.Comparator.comparingInt(PreviousEvaluationContext::score))
                .orElseThrow();
        String overallFeedback = buildCareerReport(request, weakest);
        return new FinalEvaluationResponse(evaluations, totalScore, false, overallFeedback);
    }

    /**
     * 문항 하나를 평가한다. 5개 루브릭은 8/4/3/3/2 만점 비율로 배분하며
     * 최대 나머지 방식으로 정수 합계가 항상 {@code score}와 일치하게 만든다.
     */
    private QuestionEvaluation buildEvaluation(QuestionAnswerPair pair, String userName) {
        int[] rubricScores = distributeRubricScores();
        return new QuestionEvaluation(
                pair.turn(),
                scorePerQuestion,
                rubricScores[0],
                rubricScores[1],
                rubricScores[2],
                rubricScores[3],
                rubricScores[4],
                userName + "님, 핵심 개념을 잘 이해하고 있습니다. 구체적인 사례를 추가하면 더 좋겠습니다."
        );
    }

    /** 정수 나눗셈의 나머지가 큰 루브릭부터 남은 점수를 1점씩 배분한다. */
    private int[] distributeRubricScores() {
        int[] scores = new int[RUBRIC_MAX_SCORES.length];
        int[] remainders = new int[RUBRIC_MAX_SCORES.length];
        int allocated = 0;
        for (int index = 0; index < RUBRIC_MAX_SCORES.length; index++) {
            int weightedScore = scorePerQuestion * RUBRIC_MAX_SCORES[index];
            scores[index] = weightedScore / RUBRIC_TOTAL_MAX;
            remainders[index] = weightedScore % RUBRIC_TOTAL_MAX;
            allocated += scores[index];
        }

        int remaining = scorePerQuestion - allocated;
        while (remaining > 0) {
            int target = largestRemainderIndex(scores, remainders);
            scores[target]++;
            remainders[target] = -1;
            remaining--;
        }
        return scores;
    }

    /** 아직 만점에 도달하지 않은 루브릭 중 비례 배분 나머지가 가장 큰 항목을 고른다. */
    private int largestRemainderIndex(int[] scores, int[] remainders) {
        int target = -1;
        for (int index = 0; index < remainders.length; index++) {
            if (scores[index] < RUBRIC_MAX_SCORES[index]
                    && (target < 0 || remainders[index] > remainders[target])) {
                target = index;
            }
        }
        if (target < 0) {
            throw new IllegalStateException("Mock 루브릭 점수를 배분할 수 없습니다.");
        }
        return target;
    }

    /** Mock 모드에서도 운영 응답과 동일한 4개 섹션의 최종 커리어 리포트를 반환한다. */
    private String buildCareerReport(
            EvaluationRequest request,
            PreviousEvaluationContext weakest) {
        QuestionAnswerPair followUp = request.questionAnswerPairs().get(0);
        return """
                🎯 총평
                %s님은 핵심 개념을 논리적으로 설명했지만, 선택의 효과를 운영 수치로 증명하는 부분은 더 보완할 필요가 있습니다.

                ✨ 이런 점이 매우 훌륭했어요
                - `%s` 질문에서 핵심 판단 기준을 설명하려는 접근이 좋았습니다.
                - 꼬리질문 `%s`에도 답변하며 최초 피드백을 보완하려는 문제 해결 흐름을 보여주었습니다.

                🚀 합격을 확정 짓는 2%%
                `%s`라는 피드백처럼 실제 트래픽, 응답 시간, 쿼리 수와 장애 상황을 정량적으로 연결하는 설명이 부족했습니다.

                💡 Next Step
                ❌ AS-IS (지원자의 기존 답변 방식)
                `%s`

                ⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)
                ※ 아래 수치는 답변 구조를 보여주기 위한 가상 예시이며, 실제 측정 결과가 아닙니다.
                답변에서 설명한 해결 방식을 적용하기 전후로 [예: p95 응답 시간 320ms → 140ms]와 [예: 요청당 쿼리 수 12회 → 3회]를 비교했다고 설명하세요.
                """.formatted(
                request.userName(),
                weakest.questionText(),
                followUp.questionText(),
                weakest.feedback(),
                weakest.userAnswer());
    }

    private int findWeakestTurn(List<QuestionEvaluation> evaluations) {
        return evaluations.stream()
                .min(java.util.Comparator.comparingInt(QuestionEvaluation::score))
                .map(QuestionEvaluation::turn)
                .orElse(1);
    }
}
