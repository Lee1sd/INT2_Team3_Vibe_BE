package com.careerdungeon.domain.judgment.llm;

import com.careerdungeon.domain.judgment.llm.dto.EvaluationRequest;
import com.careerdungeon.domain.judgment.llm.dto.RawFinalEvaluationResponse;
import com.careerdungeon.domain.judgment.llm.dto.RawInitialEvaluationResponse;

/**
 * 답변 채점 원시값을 제공하는 포트.
 *
 * <p>현재는 {@code MockEvaluationLlmClient}가 구현하며, Claude 연동 시에도 judgment 도메인은
 * 벤더 SDK가 아니라 이 계약만 소비한다.
 */
public interface EvaluationLlmClient {

    /**
     * 최초 세 문항을 채점해 최저점 문항을 식별할 원시 평가를 생성한다.
     *
     * @param request questionId 1~3의 질문·답변·모범답변
     * @return 서버 보정 전 최초 평가 응답
     */
    RawInitialEvaluationResponse evaluateInitial(EvaluationRequest request);

    /**
     * 꼬리질문 한 문항을 채점해 최초 확정 점수와 합산할 원시 평가를 생성한다.
     *
     * @param request questionId 4의 질문·답변·모범답변과 종합 피드백용 최초 1~3 확정 평가 컨텍스트
     * @return 서버 보정 전 최종 평가 응답
     */
    RawFinalEvaluationResponse evaluateFinal(EvaluationRequest request);
}
