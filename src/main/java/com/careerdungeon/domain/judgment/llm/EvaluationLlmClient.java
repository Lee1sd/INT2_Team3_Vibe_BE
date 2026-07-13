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

    /**
     * 질문·사용자 답변·모범답변을 받아 LLM 형식의 원시 채점 결과를 생성한다.
     *
     * @param request 채점에 필요한 질문-답변 쌍과 사용자 문맥
     * @return 서버 보정 전 원시 평가 응답
     */
    RawEvaluationResponse evaluate(EvaluationRequest request);
}
