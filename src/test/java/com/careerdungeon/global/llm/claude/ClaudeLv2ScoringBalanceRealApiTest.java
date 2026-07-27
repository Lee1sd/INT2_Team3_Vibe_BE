package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.config.RetryConfig;
import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
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
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #164 Lv.2 채점 프롬프트 변경 전후를 동일 입력으로 비교하는 수동 비용 발생 테스트다.
 *
 * <p>질문 생성의 비결정성을 통제하기 위해 커머스 백엔드 이력서와 DB 키워드에서 파생된
 * 질문·모범답안·사용자 답변을 고정한다. 기본 테스트에서는 비활성화되며
 * {@code -DrunClaudeLv2BalanceTest=true}를 명시했을 때만 실제 Claude API를 호출한다.
 */
@EnabledIfSystemProperty(named = "runClaudeLv2BalanceTest", matches = "true")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RetryConfig.class,
        LlmResponseValidator.class,
        LlmInvocationService.class,
        ClaudeLv2ScoringBalanceRealApiTest.TestConfig.class
})
class ClaudeLv2ScoringBalanceRealApiTest {

    private static final int SAMPLE_COUNT =
            Integer.parseInt(System.getProperty("lv2BalanceSampleCount", "10"));
    private static final String PROMPT_VARIANT =
            System.getProperty("lv2BalancePromptVariant", "current");
    private static final String BASELINE_PROMPT_ROOT = "prompts/lv2-balance-baseline/";

    @TestConfiguration
    static class TestConfig {

        /** gitignore된 로컬 설정만 사용해 실제 Claude 클라이언트의 호출 횟수까지 관찰한다. */
        @Bean
        LlmClient llmClient() {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application-local.yml"));
            Properties properties = yaml.getObject();
            if (properties == null) {
                throw new IllegalStateException("application-local.yml을 읽을 수 없습니다.");
            }

            String apiKey = requiredApiKey(properties);
            String model = properties.getProperty("llm.model", "claude-haiku-4-5");
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

        /** 환경변수를 우선 사용하고 로컬 설정에는 실제 키가 있을 때만 폴백한다. */
        private static String requiredApiKey(Properties properties) {
            String value = System.getenv("LLM_ANTHROPIC_API_KEY");
            if (value == null || value.isBlank()) {
                value = System.getenv("ANTHROPIC_API_KEY");
            }
            if (value == null || value.isBlank()) {
                value = properties.getProperty("llm.anthropic.api-key");
            }
            if (value != null && value.contains("${")) {
                value = null;
            }
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("ANTHROPIC_API_KEY 설정이 필요합니다.");
            }
            return value;
        }
    }

    @Autowired
    LlmInvocationService invocationService;

    @Autowired
    LlmClient llmClient;

    @Test
    @DisplayName("#164 Lv.2 동일 답변 10회 전체 채점 표본을 출력한다")
    void measureTenFixedLv2ScoringSamples() {
        for (int sample = 1; sample <= SAMPLE_COUNT; sample++) {
            EvaluationRequest initialRequest = initialRequest();
            InitialEvaluationResponse initial = invocationService.evaluateInitialAnswers(
                    initialRequest,
                    scoringPrompt(initialRequest, false));

            EvaluationRequest finalRequest = finalRequest(initial);
            FinalEvaluationResponse followUp = invocationService.evaluateFinalAnswers(
                    finalRequest,
                    scoringPrompt(finalRequest, true));

            int initialScore = initial.evaluations().stream()
                    .mapToInt(evaluation -> evaluation.score())
                    .sum();
            int followUpScore = followUp.evaluations().get(0).score();
            int finalScore = initialScore + followUpScore;

            System.out.printf(
                    "LV2_BALANCE_VARIANT=%s SAMPLE=%d INITIAL=%d FOLLOW_UP=%d FINAL=%d PASSED=%s WEAKEST=%d%n",
                    PROMPT_VARIANT,
                    sample,
                    initialScore,
                    followUpScore,
                    finalScore,
                    finalScore >= 80,
                    initial.weakestQuestionId());

            assertThat(initial.evaluations()).hasSize(4);
            assertThat(followUp.evaluations()).hasSize(1);
            assertThat(followUp.evaluations().get(0).turn()).isEqualTo(5);
            assertThat(finalScore).isBetween(0, 100);
        }

        long initialCalls = countCalls("evaluateInitialAnswers");
        long finalCalls = countCalls("evaluateFinalAnswers");
        System.out.printf(
                "LV2_BALANCE_PROVIDER_CALLS INITIAL=%d FINAL=%d TOTAL=%d%n",
                initialCalls,
                finalCalls,
                initialCalls + finalCalls);
    }

    /** current는 운영 템플릿을, baseline은 변경 전 #164 템플릿 스냅샷을 사용한다. */
    private com.careerdungeon.global.llm.dto.LlmPrompt scoringPrompt(
            EvaluationRequest request,
            boolean finalEvaluation) {
        if ("current".equalsIgnoreCase(PROMPT_VARIANT)) {
            return finalEvaluation
                    ? ScoringPromptTemplate.finalPrompt(request)
                    : ScoringPromptTemplate.initialPrompt(request);
        }
        if (!"baseline".equalsIgnoreCase(PROMPT_VARIANT)) {
            throw new IllegalArgumentException(
                    "lv2BalancePromptVariant는 baseline 또는 current여야 합니다: " + PROMPT_VARIANT);
        }

        String system = loadBaselineTemplate("system.txt");
        String user = finalEvaluation
                ? renderBaseline(
                        loadBaselineTemplate("final-user.txt"),
                        Map.of(
                                "personaTone", request.personaTone(),
                                "userName", request.userName(),
                                "turn5", formatPairs(request.questionAnswerPairs()),
                                "previousEvaluations", formatPrevious(request.previousEvaluations())))
                : renderBaseline(
                        loadBaselineTemplate("initial-user.txt"),
                        Map.of(
                                "personaTone", request.personaTone(),
                                "userName", request.userName(),
                                "questionAnswerPairs", formatPairs(request.questionAnswerPairs())));
        return new com.careerdungeon.global.llm.dto.LlmPrompt(system, user);
    }

    /** 기준선 템플릿의 토큰만 한 번 치환해 현재 운영 조립기와 같은 입력을 만든다. */
    private String renderBaseline(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    /** 질문·답변·예상답변을 운영 템플릿과 동일한 문자열 형식으로 직렬화한다. */
    private String formatPairs(List<QuestionAnswerPair> pairs) {
        StringBuilder builder = new StringBuilder();
        for (QuestionAnswerPair pair : pairs) {
            builder.append("- turn ").append(pair.turn())
                    .append("\n  question: ").append(pair.questionText())
                    .append("\n  userAnswer: ").append(pair.userAnswer())
                    .append("\n  expectedAnswer: ").append(pair.expectedAnswer())
                    .append('\n');
        }
        return builder.toString();
    }

    /** 최초 확정 평가를 변경 전 리포트 프롬프트 형식으로 직렬화한다. */
    private String formatPrevious(List<PreviousEvaluationContext> contexts) {
        StringBuilder builder = new StringBuilder();
        for (PreviousEvaluationContext context : contexts) {
            builder.append("- turn ").append(context.turn())
                    .append("\n  question: ").append(context.questionText())
                    .append("\n  userAnswer: ").append(context.userAnswer())
                    .append("\n  confirmedScore: ").append(context.score())
                    .append("\n  confirmedFeedback: ").append(context.feedback())
                    .append('\n');
        }
        return builder.toString();
    }

    /** Git에 비밀값 없이 보관한 변경 전 프롬프트 스냅샷을 UTF-8로 읽는다. */
    private String loadBaselineTemplate(String fileName) {
        ClassPathResource resource = new ClassPathResource(BASELINE_PROMPT_ROOT + fileName);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("기준선 프롬프트를 읽을 수 없습니다: " + fileName, e);
        }
    }

    /** 고정된 최초 네 문항으로 프롬프트 변경 외 변수를 통제한다. */
    private EvaluationRequest initialRequest() {
        return EvaluationRequest.initial(List.of(
                new QuestionAnswerPair(
                        1,
                        "주문 재고 차감에서 SELECT FOR UPDATE를 사용했을 때 동시성 문제가 어떻게 방지되고, "
                                + "트래픽이 몰릴 때 어떤 한계와 대안을 고려해야 하나요?",
                        "트랜잭션 안에서 재고 행을 SELECT FOR UPDATE로 읽으면 배타 락이 걸려 다른 차감 요청은 "
                                + "커밋까지 대기하므로 lost update를 막을 수 있습니다. 다만 인기 상품은 락 대기와 "
                                + "타임아웃이 늘 수 있어 트랜잭션을 짧게 유지하고 락 대기 시간을 관찰해야 합니다. "
                                + "충돌이 드물면 버전 컬럼을 이용한 낙관적 락과 제한된 재시도를 대안으로 비교하겠습니다.",
                        "배타 행 잠금으로 동시 갱신을 직렬화해 lost update를 방지한다. 높은 경합에서는 대기 시간과 "
                                + "교착·타임아웃 비용이 커지므로 트랜잭션 범위를 줄이고 락 순서를 통일한다. 충돌 빈도에 "
                                + "따라 낙관적 락, Redis 원자 연산, 큐 기반 직렬화 같은 대안을 비교한다."),
                new QuestionAnswerPair(
                        2,
                        "대용량 주문 목록을 offset 페이지네이션에서 커서 방식으로 바꿀 때 인덱스를 어떻게 설계하고 "
                                + "정합성 측면에서 무엇을 확인해야 하나요?",
                        "offset은 뒤 페이지로 갈수록 앞의 행을 읽고 버리는 비용이 커집니다. created_at과 id를 "
                                + "복합 커서로 사용하고 같은 순서의 복합 인덱스를 두면 마지막 조회 위치 이후를 범위 탐색할 "
                                + "수 있습니다. created_at 동률을 id로 정렬해 중복과 누락을 줄이고, 임의 페이지 이동이 "
                                + "필요한 관리 화면에는 offset을 유지할 수 있습니다.",
                        "정렬 조건과 커서 조건에 맞는 복합 인덱스를 설계하고 마지막 키 이후를 범위 조회한다. 동률을 "
                                + "해결할 고유 보조 키를 사용해 중복·누락을 방지한다. 커서 방식의 일정한 조회 비용과 "
                                + "offset 방식의 임의 페이지 이동 장점을 사용 사례에 따라 비교한다."),
                new QuestionAnswerPair(
                        3,
                        "주문 트랜잭션과 Kafka 이벤트 발행 사이의 이중 쓰기 문제를 어떻게 해결하고, 중복 이벤트는 "
                                + "어떻게 안전하게 처리하시겠습니까?",
                        "주문 저장과 outbox 레코드 저장을 같은 DB 트랜잭션으로 묶고 별도 릴레이가 outbox를 Kafka로 "
                                + "발행하게 하겠습니다. 발행 후 상태 갱신 전에 장애가 나면 중복 발행될 수 있으므로 이벤트 "
                                + "ID를 두고 소비자가 처리 이력을 저장해 멱등하게 처리합니다. 릴레이 실패는 재시도하고 "
                                + "미발행 건수와 지연 시간을 모니터링하겠습니다.",
                        "주문과 outbox를 같은 로컬 트랜잭션에 기록해 원자성을 확보하고 릴레이 또는 CDC로 발행한다. "
                                + "at-least-once 전달에서 중복이 발생할 수 있으므로 이벤트 ID, 소비자 처리 이력, 고유 "
                                + "제약으로 멱등성을 보장한다. 재시도, 순서 보장 범위, poison message와 모니터링을 고려한다."),
                new QuestionAnswerPair(
                        4,
                        "Redis cache-aside를 적용한 상품 조회에서 DB와 캐시의 정합성을 어떤 순서로 관리하고, 장애 시 "
                                + "어떤 폴백을 두시겠습니까?",
                        "조회는 캐시 miss일 때 DB를 읽고 TTL과 함께 캐시에 저장합니다. 수정은 DB 커밋 후 캐시를 "
                                + "삭제해 다음 조회가 새 값을 채우게 하고, 삭제 실패는 재시도 큐로 보냅니다. Redis 장애 "
                                + "시에는 DB로 폴백하되 동시 요청이 몰리지 않도록 짧은 로컬 캐시나 요청 병합을 검토하고 "
                                + "캐시 적중률과 DB 부하를 함께 관찰하겠습니다.",
                        "cache-aside 조회와 DB 쓰기 후 캐시 무효화 순서를 설명한다. 삭제 실패 재시도, TTL, 지연 이중 "
                                + "삭제나 이벤트 기반 무효화로 stale window를 제한한다. Redis 장애 시 DB 폴백과 "
                                + "cache stampede 방어, 적중률·DB 부하·불일치 모니터링을 고려한다.")),
                "STRICT",
                "테스터");
    }

    /** 최초 평가를 읽기 전용 리포트 컨텍스트로 변환하고 고정 꼬리답변을 연결한다. */
    private EvaluationRequest finalRequest(InitialEvaluationResponse initial) {
        List<QuestionAnswerPair> initialPairs = initialRequest().questionAnswerPairs();
        List<PreviousEvaluationContext> previous = initial.evaluations().stream()
                .map(evaluation -> {
                    QuestionAnswerPair pair = initialPairs.stream()
                            .filter(candidate -> candidate.turn() == evaluation.turn())
                            .findFirst()
                            .orElseThrow();
                    return new PreviousEvaluationContext(
                            evaluation.turn(),
                            pair.questionText(),
                            pair.userAnswer(),
                            evaluation.score(),
                            evaluation.feedback());
                })
                .toList();

        return EvaluationRequest.finalEvaluation(
                List.of(new QuestionAnswerPair(
                        5,
                        "Outbox 릴레이가 이벤트 발행에는 성공했지만 발행 완료 상태를 저장하기 전에 장애가 났다면, "
                                + "재시작 후 어떤 문제가 생기고 어떻게 방어하시겠습니까?",
                        "재시작한 릴레이가 같은 outbox를 다시 읽어 중복 발행할 수 있습니다. 발행 자체와 DB 상태 갱신을 "
                                + "하나의 원자 작업으로 만들기 어렵기 때문에 at-least-once를 전제로 이벤트 ID를 유지하고, "
                                + "소비자 DB에 처리된 이벤트 ID를 고유 키로 저장해 같은 이벤트의 비즈니스 로직을 한 번만 "
                                + "적용하겠습니다. 릴레이는 재시도 횟수와 마지막 오류를 기록하고 오래 막힌 이벤트를 "
                                + "알림으로 확인하겠습니다.",
                        "발행 성공 후 상태 저장 전 장애에서는 동일 outbox가 재처리되어 중복 이벤트가 발생한다. "
                                + "at-least-once를 전제로 고유 이벤트 ID와 소비자 처리 이력의 UNIQUE 제약을 사용해 "
                                + "멱등 처리한다. 릴레이 재시도, 교착된 이벤트 격리, 순서 보장 단위와 지연 모니터링을 설명한다.")),
                previous,
                "STRICT",
                "테스터");
    }

    /** 오버로드와 재시도를 포함한 실제 벤더 호출 횟수를 메서드명으로 집계한다. */
    private long countCalls(String methodName) {
        return Mockito.mockingDetails(llmClient).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(methodName)
                        && invocation.getArguments().length == 2)
                .count();
    }
}
