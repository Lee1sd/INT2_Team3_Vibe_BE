package com.careerdungeon.global.llm.mock;

import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.EvaluationResponse;
import com.careerdungeon.global.llm.dto.GeneratedQuestion;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.dto.QuestionGenerationRequest;
import com.careerdungeon.global.llm.dto.QuestionGenerationResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 실 LLM API 호출 없이 고정 응답을 반환하는 Mock 구현체.
 * llm.mode=mock(기본값)일 때 Bean으로 등록된다 (NFR-11, llm-cost-policy.md §1).
 *
 * 스키마 검증 실패 분기 테스트는 이 클래스를 상속하거나 별도 테스트 픽스처로 구현한다.
 */
@Component
@ConditionalOnProperty(name = "llm.mode", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final int MOCK_SCORE_PER_QUESTION = 18;

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
    public EvaluationResponse evaluateAnswers(EvaluationRequest request) {
        List<QuestionEvaluation> evaluations = request.questionAnswerPairs().stream()
                .map(pair -> buildEvaluation(pair, request.userName()))
                .toList();

        int totalScore = evaluations.stream().mapToInt(QuestionEvaluation::score).sum();
        int weakestQuestionId = findWeakestTurn(evaluations);

        return new EvaluationResponse(evaluations, totalScore, weakestQuestionId, totalScore >= 80);
    }

    private QuestionEvaluation buildEvaluation(QuestionAnswerPair pair, String userName) {
        return new QuestionEvaluation(
                pair.turn(),
                MOCK_SCORE_PER_QUESTION,
                userName + "님, 핵심 개념을 잘 이해하고 있습니다. 구체적인 사례를 추가하면 더 좋겠습니다."
        );
    }

    private int findWeakestTurn(List<QuestionEvaluation> evaluations) {
        return evaluations.stream()
                .min(java.util.Comparator.comparingInt(QuestionEvaluation::score))
                .map(QuestionEvaluation::turn)
                .orElse(1);
    }
}
