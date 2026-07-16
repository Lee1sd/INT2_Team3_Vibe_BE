package com.careerdungeon.domain.judgment.llm;

import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawQuestionEvaluation;
import com.careerdungeon.domain.judgment.llm.dto.RubricScores;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.QuestionEvaluation;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.springframework.stereotype.Component;

import java.util.List;

/** global.llm 평가 응답을 judgment 채점 원시 모델로 옮기는 경계 어댑터. */
@Component
public class LlmEvaluationResponseAdapter {

    public RawInitialEvaluationResponse toRawInitial(InitialEvaluationResponse response) {
        if (response == null) {
            throw schemaError("InitialEvaluationResponse가 null입니다.");
        }
        return new RawInitialEvaluationResponse(
                mapEvaluations(response.evaluations()),
                response.totalScore(),
                response.weakestQuestionId(),
                response.passed());
    }

    public RawFinalEvaluationResponse toRawFinal(FinalEvaluationResponse response) {
        if (response == null) {
            throw schemaError("FinalEvaluationResponse가 null입니다.");
        }
        return new RawFinalEvaluationResponse(
                mapEvaluations(response.evaluations()),
                response.totalScore(),
                response.passed(),
                response.overallFeedback());
    }

    private List<RawQuestionEvaluation> mapEvaluations(List<QuestionEvaluation> evaluations) {
        if (evaluations == null) {
            throw schemaError("evaluations가 null입니다.");
        }
        return evaluations.stream()
                .map(this::mapEvaluation)
                .toList();
    }

    private RawQuestionEvaluation mapEvaluation(QuestionEvaluation evaluation) {
        if (evaluation == null) {
            throw schemaError("evaluations에 null 항목이 있습니다.");
        }
        return new RawQuestionEvaluation(
                evaluation.turn(),
                evaluation.score(),
                new RubricScores(
                        evaluation.technicalAccuracy(),
                        evaluation.coreCoverage(),
                        evaluation.reasoning(),
                        evaluation.specificity(),
                        evaluation.tradeOffsAndExceptions()),
                evaluation.feedback());
    }

    private LlmSchemaValidationException schemaError(String message) {
        return new LlmSchemaValidationException(message);
    }
}
