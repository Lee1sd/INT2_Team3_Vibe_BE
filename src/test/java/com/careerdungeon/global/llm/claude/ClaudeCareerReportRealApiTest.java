package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.config.RetryConfig;
import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.prompt.ScoringPromptTemplate;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Claude가 최종 커리어 리포트 계약을 지키는지 수동으로 확인하는 비용 발생 테스트다.
 *
 * <p>기본 테스트에서는 비활성화되며
 * {@code -DrunClaudeCareerReportTest=true}를 명시했을 때만 실행한다.
 */
@EnabledIfSystemProperty(named = "runClaudeCareerReportTest", matches = "true")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RetryConfig.class,
        LlmResponseValidator.class,
        LlmInvocationService.class,
        ClaudeCareerReportRealApiTest.TestConfig.class
})
class ClaudeCareerReportRealApiTest {

    private static final int MAX_SAMPLE_COUNT = 20;
    private static final int SAMPLE_COUNT = sampleCount();

    @TestConfiguration
    static class TestConfig {

        /** 배포 서버와 같은 환경변수 계약으로 실제 Claude 클라이언트 spy를 구성한다. */
        @Bean
        LlmClient llmClient() {
            String apiKey = firstConfiguredEnvironment(
                    "LLM_ANTHROPIC_API_KEY",
                    "ANTHROPIC_API_KEY");
            String model = environmentOrDefault("LLM_MODEL", "claude-haiku-4-5");
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(5));
            requestFactory.setReadTimeout(Duration.ofSeconds(30));

            ClaudeLlmClient realClient = new ClaudeLlmClient(
                    RestClient.builder(),
                    new ObjectMapper(),
                    model,
                    apiKey,
                    "https://api.anthropic.com",
                    "2023-06-01",
                    2048,
                    requestFactory);
            return Mockito.spy(realClient);
        }

        /** 후보 환경변수 중 처음 설정된 비밀값을 반환하고 값 자체는 로그에 남기지 않는다. */
        private static String firstConfiguredEnvironment(String... names) {
            for (String name : names) {
                String value = System.getenv(name);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            throw new IllegalStateException(String.join(" 또는 ", names) + " 환경변수가 필요합니다.");
        }

        /** 배포 환경과 같은 모델 환경변수를 사용하되 미설정이면 확정 기본 모델을 사용한다. */
        private static String environmentOrDefault(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }

    @Autowired
    LlmInvocationService invocationService;

    @Autowired
    LlmClient llmClient;

    @Test
    @DisplayName("실 Claude 최종판정 10표본은 모두 실제 면접 기반 정상 리포트를 반환한다")
    void realClaudeFinalEvaluationAlwaysReturnsContextualReport() {
        EvaluationRequest request = finalEvaluationRequest();
        List<FinalEvaluationResponse> responses = new ArrayList<>();

        for (int sample = 0; sample < SAMPLE_COUNT; sample++) {
            responses.add(invocationService.evaluateFinalAnswers(
                    request,
                    ScoringPromptTemplate.finalPrompt(request)));
        }

        long callCount = Mockito.mockingDetails(llmClient).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("evaluateFinalAnswers")
                        && invocation.getArguments().length == 2)
                .count();
        long normalReportCount = responses.stream()
                .filter(response -> !response.overallFeedback().equals(
                        com.careerdungeon.global.llm.validation.CareerReportValidator.FALLBACK_REPORT))
                .count();
        System.out.println("CLAUDE_FINAL_CALL_COUNT=" + callCount);
        System.out.println("CLAUDE_CAREER_REPORT_NORMAL_COUNT=" + normalReportCount);

        assertThat(responses).hasSize(SAMPLE_COUNT).allSatisfy(response -> {
            assertThat(response.evaluations()).hasSize(1);
            assertThat(response.evaluations().get(0).turn()).isEqualTo(5);
            assertThat(response.totalScore()).isNotNull();
            assertThat(response.passed()).isNotNull();
            assertThat(response.overallFeedback())
                    .contains("🎯 총평", "✨ 이런 점이 매우 훌륭했어요")
                    .contains("🚀 합격을 확정 짓는 2%", "💡 Next Step")
                    .contains("❌ AS-IS", "⭕ TO-BE")
                    .containsAnyOf("Redis", "캐시", "JPA", "트랜잭션")
                    .contains(com.careerdungeon.global.llm.validation.CareerReportValidator.HYPOTHETICAL_DISCLAIMER)
                    .doesNotContain(com.careerdungeon.global.llm.validation.CareerReportValidator.FALLBACK_REPORT)
                    .doesNotContain("turn", "expectedAnswer", "모범답안", "confirmedScore", "루브릭");
        });
        assertThat(normalReportCount).isEqualTo(SAMPLE_COUNT);
        assertThat(callCount).isBetween((long) SAMPLE_COUNT, (long) SAMPLE_COUNT * 2);
    }

    /** 실호출 비용을 제한하면서 기본 수용 기준인 10표본을 설정한다. */
    private static int sampleCount() {
        String configured = System.getProperty("careerReportSampleCount", "10");
        final int parsed;
        try {
            parsed = Integer.parseInt(configured);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "careerReportSampleCount는 1~" + MAX_SAMPLE_COUNT + " 사이의 정수여야 합니다: "
                            + configured,
                    exception);
        }
        if (parsed < 1 || parsed > MAX_SAMPLE_COUNT) {
            throw new IllegalArgumentException(
                    "careerReportSampleCount는 1~" + MAX_SAMPLE_COUNT + " 사이여야 합니다: " + parsed);
        }
        return parsed;
    }

    /** 기존 네 문항과 꼬리질문이 구체적으로 이어지는 실호출 입력을 만든다. */
    private EvaluationRequest finalEvaluationRequest() {
        return EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(
                        5,
                        "Redis 캐시와 DB 사이의 정합성이 깨질 때 어떤 순서와 실패 전략으로 복구하시겠습니까?",
                        "DB 커밋 후 캐시를 삭제하고 삭제 실패는 재시도 큐로 보냅니다. "
                                + "다만 동시 요청이 오래된 값을 다시 채우는 경쟁 조건까지는 설명하지 못했습니다.",
                        "DB 트랜잭션과 캐시 갱신의 원자성 한계를 설명하고, delete-after-write, "
                                + "지연 이중 삭제 또는 CDC 기반 무효화와 재시도·멱등성·모니터링을 비교한다.")),
                List.of(
                        new PreviousEvaluationContext(
                                1,
                                "JPA 조회에서 N+1 문제를 어떻게 진단하고 해결했습니까?",
                                "Hibernate 통계와 쿼리 로그로 요청당 쿼리 수를 확인한 뒤 JOIN FETCH를 적용했습니다. "
                                        + "컬렉션 페이징은 데이터 중복 때문에 별도 조회로 분리했습니다.",
                                16,
                                "진단과 해결 선택은 타당하지만 실제 전후 측정 지표가 부족했습니다."),
                        new PreviousEvaluationContext(
                                2,
                                "읽기와 쓰기 트랜잭션을 분리한 이유와 주의점을 설명해 주세요.",
                                "읽기 전용 서비스는 readOnly 트랜잭션으로 분리하고 쓰기는 짧게 유지했습니다. "
                                        + "복제 지연이 허용되지 않는 조회는 writer를 사용했습니다.",
                                17,
                                "트레이드오프와 예외 조건을 명확히 설명했습니다."),
                        new PreviousEvaluationContext(
                                3,
                                "Redis cache-aside를 선택한 이유와 만료 정책을 설명해 주세요.",
                                "조회 비중이 높은 데이터를 cache-aside로 저장하고 TTL과 명시적 무효화를 함께 사용했습니다.",
                                15,
                                "구조적 선택은 좋지만 장애 시 폴백과 관측 지표가 부족했습니다."),
                        new PreviousEvaluationContext(
                                4,
                                "JPA 1차 캐시와 Redis 캐시의 차이를 설명해 주세요.",
                                "1차 캐시는 영속성 컨텍스트 범위이고 Redis는 여러 인스턴스가 공유합니다. "
                                        + "구체적인 동시성 정합성 대응은 충분히 설명하지 못했습니다.",
                                5,
                                "개념 차이는 설명했지만 다중 인스턴스 정합성 전략이 부족했습니다.")),
                "STRICT",
                "최용성");
    }
}
