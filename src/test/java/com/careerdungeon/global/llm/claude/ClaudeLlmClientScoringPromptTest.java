package com.careerdungeon.global.llm.claude;

import com.careerdungeon.domain.interview.service.ScoringPrompt;
import com.careerdungeon.domain.interview.service.ScoringPromptProvider;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClaudeLlmClientScoringPromptTest {

    private static final String BASE_URL = "https://api.anthropic.com";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("최초 채점 요청은 저장된 모범답안과 v2 고정 루브릭을 실제 Claude 프롬프트에 포함한다")
    void initialEvaluationUsesStoredExpectedAnswersAndFixedRubric() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    JsonNode body = readRequestBody(request);
                    String systemPrompt = body.path("system").asText();
                    String userPrompt = body.path("messages").get(0).path("content").asText();

                    assertThat(systemPrompt)
                            .contains("technicalAccuracy (0~8)")
                            .contains("coreCoverage (0~4)")
                            .contains("reasoning (0~3)")
                            .contains("specificity (0~3)")
                            .contains("tradeOffsAndExceptions (0~2)")
                            .contains("7~8: 질문의 핵심 개념이 정확하고")
                            .contains("형식 고정용 예시")
                            .contains("캐시는 DB 부하를 줄여서 씁니다")
                            .contains("평가 참고 기준")
                            .contains("감점 체크리스트가 아니다")
                            .contains("이전 문항의 점수·feedback");
                    assertThat(userPrompt)
                            .contains("최초 면접 답변 turn 1, 2, 3, 4")
                            .contains("아래 내용만 점수에 사용")
                            .contains("동등한 개념과 타당한 대안을 인정")
                            .contains("expectedAnswer: DB 인덱스 모범답안")
                            .contains("expectedAnswer: 격리 수준 모범답안")
                            .contains("expectedAnswer: 락 모범답안")
                            .contains("expectedAnswer: 트랜잭션 모범답안")
                            .contains("\"technicalAccuracy\"")
                            .contains("\"weakestQuestionId\"")
                            .contains("passed는 false");
                })
                .andRespond(withSuccess(claudeResponse(initialEvaluationJson()), null));

        ClaudeLlmClient sut = client(builder);
        EvaluationRequest request = initialRequest();
        ScoringPrompt prompt = new ScoringPromptProvider().initialPrompt(request);
        sut.evaluateInitialAnswers(request, toLlmPrompt(prompt));

        server.verify();
    }

    @Test
    @DisplayName("최종 채점 요청은 turn 5만 채점하고 최초 평가는 종합 피드백에만 사용한다")
    void finalEvaluationScoresOnlyTurn5AndKeepsPreviousEvaluationsReadOnly() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(this::assertFinalPromptContract)
                .andRespond(withSuccess(claudeResponse(finalEvaluationJson()), null));

        ClaudeLlmClient sut = client(builder);
        EvaluationRequest request = finalRequest();
        ScoringPrompt prompt = new ScoringPromptProvider().finalPrompt(request);
        sut.evaluateFinalAnswers(request, toLlmPrompt(prompt));

        server.verify();
    }

    @Test
    @DisplayName("최종 채점 하위 호환 호출도 리소스 프롬프트의 전체 출력 계약을 사용한다")
    void finalEvaluationFallbackUsesSameResourceContract() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(this::assertFinalPromptContract)
                .andRespond(withSuccess(claudeResponse(finalEvaluationJson()), null));

        ClaudeLlmClient sut = client(builder);
        sut.evaluateFinalAnswers(finalRequest());

        server.verify();
    }

    /** 명시적·하위 호환 호출이 공유해야 하는 최종 리포트 프롬프트 계약을 검증한다. */
    private void assertFinalPromptContract(ClientHttpRequest request) throws IOException {
        JsonNode body = readRequestBody(request);
        String userPrompt = body.path("messages").get(0).path("content").asText();

        assertThat(userPrompt)
                .contains("1단계 — turn 5 점수 산정")
                .contains("2단계 — 최종 커리어 리포트 작성")
                .contains("expectedAnswer: 캐시 정합성 모범답안")
                .contains("confirmedScore: 12")
                .contains("confirmedFeedback: 피드백1")
                .contains("question: 질문1")
                .contains("question: 질문2")
                .contains("question: 질문3")
                .contains("question: 질문4")
                .contains("다시 채점하거나 변경하지 마세요")
                .contains("overallFeedback")
                .contains("이전 답변이 낮은 점수를 받았거나 부정적인 feedback")
                .contains("turn 5 점수에는 어떤 방식으로도 반영하지 마세요")
                .contains("personaTone은 리포트의 말투와 피드백 강도에만 반영")
                .contains("참고 기준으로만 사용해 산정")
                .contains("🎯 총평")
                .contains("✨ 이런 점이 매우 훌륭했어요")
                .contains("🚀 합격을 확정 짓는 2%")
                .contains("💡 Next Step")
                .contains("❌ AS-IS (지원자의 기존 답변 방식)")
                .contains("⭕ TO-BE (수치와 정량적 지표가 포함된 이상적인 답변 방식)")
                .contains("[예: p95 240ms → 120ms]")
                // 가상 수치 고지는 모델에게 요구하지 않는다 — 서버가 항상 덧붙인다(#167).
                .doesNotContain("※ 아래 수치는 답변 구조를 보여주기 위한 가상 예시이며, 실제 측정 결과가 아닙니다.")
                .contains("실제로 측정·달성한 성과로 단정하지 마세요")
                .contains("수치 표현 전체를 하나의 `[예: ...]` 안에")
                .contains("`- `로 시작하는 불릿 2줄만")
                .contains("JSON을 반환하기 전에 overallFeedback만 다시 검사")
                .contains("`expectedAnswer`, `모범답안`")
                .contains("지정된 4개 섹션 외에")
                .contains("\"turn\":5")
                .contains("\"overallFeedback\"");
    }

    /** Mock 서버가 받은 Anthropic 요청 본문을 JSON으로 읽는다. */
    private JsonNode readRequestBody(ClientHttpRequest request) throws IOException {
        MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
        return objectMapper.readTree(mockRequest.getBodyAsString());
    }

    /** 실제 네트워크 없이 요청 본문을 검증할 Claude 클라이언트를 만든다. */
    private ClaudeLlmClient client(RestClient.Builder builder) {
        return new ClaudeLlmClient(
                builder,
                objectMapper,
                "claude-haiku-4-5",
                "test-api-key",
                BASE_URL,
                "2023-06-01",
                2048,
                null);
    }

    /** Provider가 조립한 도메인 프롬프트를 공통 LLM 호출 DTO로 변환한다. */
    private LlmPrompt toLlmPrompt(ScoringPrompt prompt) {
        return new LlmPrompt(prompt.systemPrompt(), prompt.userPrompt());
    }

    /** 테스트용 최초 turn 1~4 채점 요청을 만든다. */
    private EvaluationRequest initialRequest() {
        return EvaluationRequest.initial(List.of(
                new QuestionAnswerPair(1, "인덱스를 언제 사용하나요?", "조회가 느릴 때 사용합니다.", "DB 인덱스 모범답안"),
                new QuestionAnswerPair(2, "격리 수준을 설명하세요.", "동시성을 제어합니다.", "격리 수준 모범답안"),
                new QuestionAnswerPair(3, "락을 설명하세요.", "낙관적 락을 사용합니다.", "락 모범답안"),
                new QuestionAnswerPair(4, "트랜잭션을 설명하세요.", "원자성을 보장합니다.", "트랜잭션 모범답안")),
                "STRICT",
                "홍길동");
    }

    /** 테스트용 turn 5 단독 채점 요청을 만든다. */
    private EvaluationRequest finalRequest() {
        return EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(
                        5,
                        "캐시 정합성 문제를 어떻게 처리하나요?",
                        "수정 시 캐시를 삭제합니다.",
                        "캐시 정합성 모범답안")),
                List.of(
                        new PreviousEvaluationContext(1, "질문1", "답변1", 12, "피드백1"),
                        new PreviousEvaluationContext(2, "질문2", "답변2", 18, "피드백2"),
                        new PreviousEvaluationContext(3, "질문3", "답변3", 20, "피드백3"),
                        new PreviousEvaluationContext(4, "질문4", "답변4", 16, "피드백4")),
                "STRICT",
                "홍길동");
    }

    /** Anthropic 응답 envelope 안에 채점 JSON 문자열을 넣는다. */
    private String claudeResponse(String contentJson) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "content", List.of(Map.of("type", "text", "text", contentJson))));
    }

    /** 최초 채점 파싱 성공에 필요한 최소 JSON 응답을 반환한다. */
    private String initialEvaluationJson() {
        return """
                {"evaluations":[
                  {"turn":1,"score":10,"technicalAccuracy":4,"coreCoverage":2,"reasoning":2,"specificity":1,"tradeOffsAndExceptions":1,"feedback":"피드백1"},
                  {"turn":2,"score":12,"technicalAccuracy":5,"coreCoverage":3,"reasoning":2,"specificity":1,"tradeOffsAndExceptions":1,"feedback":"피드백2"},
                  {"turn":3,"score":14,"technicalAccuracy":6,"coreCoverage":3,"reasoning":2,"specificity":2,"tradeOffsAndExceptions":1,"feedback":"피드백3"},
                  {"turn":4,"score":11,"technicalAccuracy":4,"coreCoverage":3,"reasoning":2,"specificity":1,"tradeOffsAndExceptions":1,"feedback":"피드백4"}
                ],"totalScore":47,"weakestQuestionId":1,"passed":false}
                """;
    }

    /** 최종 채점 파싱 성공에 필요한 최소 JSON 응답을 반환한다. */
    private String finalEvaluationJson() {
        return """
                {"evaluations":[
                  {"turn":5,"score":15,"technicalAccuracy":6,"coreCoverage":3,"reasoning":3,"specificity":2,"tradeOffsAndExceptions":1,"feedback":"꼬리질문 피드백"}
                ],"totalScore":15,"passed":false,"overallFeedback":"종합 피드백"}
                """;
    }
}
