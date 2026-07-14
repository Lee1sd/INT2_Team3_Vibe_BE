package com.careerdungeon.global.llm.mock;

import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 실 LLM API 호출 없이 고정 응답을 반환하는 Mock 구현체.
 * llm.mode=mock(기본값)일 때 Bean으로 등록된다 (NFR-11, llm-cost-policy.md §1).
 *
 * 합격/불합격 시나리오 전환:
 * - application-local.yml에서 {@code llm.mock.score-per-question} 조정 (기본값 18 → 불합격)
 * - 합격 케이스: 4문항 기준 문항당 20점 이상 필요 (4×20=80 ≥ 80)
 * - 단위 테스트: {@link #MockLlmClient(int)} 생성자로 점수 직접 주입
 *
 * <p>최초 채점(turn 1~3)과 최종 채점(turn 1~4)은 동일한 방식으로 각 문항을 독립 평가한다
 * (ADR-010) — 최종 채점이라고 해서 이전 문항을 재평가 없이 고정값으로 채우지 않는다.
 */
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final int TECHNICAL_ACCURACY_MAX = 10;
    private static final int CORE_COVERAGE_MAX = 5;
    private static final int REASONING_MAX = 4;
    private static final int SPECIFICITY_MAX = 3;
    private static final int RUBRIC_TOTAL_MAX = 25;

    private final int scorePerQuestion;

    public MockLlmClient(@Value("${llm.mock.score-per-question:18}") int scorePerQuestion) {
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
                        "Stateless, 자원 중심 URI, HTTP 메서드 의미 준수, 적절한 상태코드 반환")
        ));
    }

    @Override
    public InitialEvaluationResponse evaluateInitialAnswers(EvaluationRequest request) {
        List<QuestionEvaluation> evaluations = request.questionAnswerPairs().stream()
                .map(pair -> buildEvaluation(pair, request.userName()))
                .toList();
        int weakestQuestionId = findWeakestTurn(evaluations);
        int totalScore = evaluations.stream().mapToInt(QuestionEvaluation::score).sum();
        return new InitialEvaluationResponse(evaluations, totalScore, weakestQuestionId, totalScore >= 80);
    }

    @Override
    public FinalEvaluationResponse evaluateFinalAnswers(EvaluationRequest request) {
        List<QuestionEvaluation> evaluations = request.questionAnswerPairs().stream()
                .map(pair -> buildEvaluation(pair, request.userName()))
                .toList();
        int totalScore = evaluations.stream().mapToInt(QuestionEvaluation::score).sum();
        String overallFeedback = request.userName() + "님, 꼬리질문까지 반영한 최종 평가입니다.";
        return new FinalEvaluationResponse(evaluations, totalScore, totalScore >= 80, overallFeedback);
    }

    /**
     * 문항 하나를 평가한다. 5개 루브릭은 {@code scorePerQuestion}을 각 루브릭 만점 비율
     * (10/5/4/3/3)에 비례 배분해 채운다 — 합계가 항상 {@code score}와 일치한다.
     */
    private QuestionEvaluation buildEvaluation(QuestionAnswerPair pair, String userName) {
        int technicalAccuracy = rubricShare(TECHNICAL_ACCURACY_MAX);
        int coreCoverage = rubricShare(CORE_COVERAGE_MAX);
        int reasoning = rubricShare(REASONING_MAX);
        int specificity = rubricShare(SPECIFICITY_MAX);
        int tradeOffsAndExceptions = scorePerQuestion - technicalAccuracy - coreCoverage - reasoning - specificity;
        return new QuestionEvaluation(
                pair.turn(),
                scorePerQuestion,
                technicalAccuracy,
                coreCoverage,
                reasoning,
                specificity,
                tradeOffsAndExceptions,
                userName + "님, 핵심 개념을 잘 이해하고 있습니다. 구체적인 사례를 추가하면 더 좋겠습니다."
        );
    }

    private int rubricShare(int rubricMax) {
        return Math.round(scorePerQuestion * rubricMax / (float) RUBRIC_TOTAL_MAX);
    }

    private int findWeakestTurn(List<QuestionEvaluation> evaluations) {
        return evaluations.stream()
                .min(java.util.Comparator.comparingInt(QuestionEvaluation::score))
                .map(QuestionEvaluation::turn)
                .orElse(1);
    }
}
