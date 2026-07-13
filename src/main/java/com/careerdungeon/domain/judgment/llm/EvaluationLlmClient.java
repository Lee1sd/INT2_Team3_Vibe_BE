package com.careerdungeon.domain.judgment.llm;

import com.careerdungeon.domain.judgment.llm.dto.EvaluationRequest;
import com.careerdungeon.domain.judgment.llm.dto.RawEvaluationResponse;

/**
 * 답변 채점 원시값을 제공하는 포트.
 *
 * <p>현재는 {@code MockEvaluationLlmClient}가 구현하며, Claude 연동 시에도 judgment 도메인은
 * 벤더 SDK가 아니라 이 계약만 소비한다.
 */
public interface EvaluationLlmClient {

    RawEvaluationResponse evaluate(EvaluationRequest request);
}
