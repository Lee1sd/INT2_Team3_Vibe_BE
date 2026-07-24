package com.careerdungeon.global.llm.claude;

import com.careerdungeon.domain.interview.service.QuestionGenerationPrompt;
import com.careerdungeon.domain.interview.service.QuestionGenerationPromptProvider;
import com.careerdungeon.domain.persona.PersonaPromptProvider;
import com.careerdungeon.global.config.RetryConfig;
import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FollowUpGenerationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.prompt.ScoringPromptTemplate;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델비교 Phase 2 최소 범위 — 최초채점·꼬리질문생성을 기존 타임아웃(30s/2048)으로
 * 독립 호출해 Sonnet의 타임아웃/응답 잘림 여부만 확인하는 수동 비용 발생 테스트다.
 *
 * <p>{@code ClaudeCareerReportRealApiTest}와 동일한 패턴 — 컨트롤러/DB를 거치지 않고
 * {@link LlmInvocationService} + 실제 {@link ClaudeLlmClient}만으로 각 단계를 독립 호출한다.
 * 기본 테스트에서는 비활성화되며 {@code -DrunClaudeInitialFollowUpTest=true}를 명시했을 때만 실행한다.
 */
@EnabledIfSystemProperty(named = "runClaudeInitialFollowUpTest", matches = "true")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RetryConfig.class,
        LlmResponseValidator.class,
        LlmInvocationService.class,
        ClaudeInitialAndFollowUpRealApiTest.TestConfig.class
})
class ClaudeInitialAndFollowUpRealApiTest {

    @TestConfiguration
    static class TestConfig {

        /** gitignore된 로컬 설정의 API 키로 실제 Claude 클라이언트를 구성한다. */
        @Bean
        LlmClient llmClient() {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(new ClassPathResource("application-local.yml"));
            Properties properties = yaml.getObject();
            if (properties == null) {
                throw new IllegalStateException("application-local.yml을 읽을 수 없습니다.");
            }

            String apiKey = requiredProperty(properties, "llm.anthropic.api-key");
            String model = requiredSonnetModel(properties);
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofSeconds(5));
            requestFactory.setReadTimeout(Duration.ofSeconds(30));

            return new ClaudeLlmClient(
                    RestClient.builder(),
                    new ObjectMapper(),
                    model,
                    apiKey,
                    "https://api.anthropic.com",
                    "2023-06-01",
                    2048,
                    requestFactory);
        }

        private static String requiredProperty(Properties properties, String key) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(key + " 설정이 필요합니다.");
            }
            return value;
        }

        /** Phase 2는 Sonnet 전용 검증이므로 llm.model이 없거나 Sonnet이 아니면 즉시 실패시킨다. */
        private static String requiredSonnetModel(Properties properties) {
            String model = properties.getProperty("llm.model");
            if (model == null || model.isBlank() || !model.toLowerCase().contains("sonnet")) {
                throw new IllegalStateException(
                        "이 테스트는 Sonnet 전용입니다. application-local.yml의 llm.model을 "
                                + "claude-sonnet-4-6으로 설정하세요. 현재 값: " + model);
            }
            return model;
        }
    }

    @Autowired
    LlmInvocationService invocationService;

    private final PersonaPromptProvider personaPromptProvider = new PersonaPromptProvider();
    private final QuestionGenerationPromptProvider followUpPromptProvider =
            new QuestionGenerationPromptProvider(personaPromptProvider);

    @Test
    @DisplayName("Phase 2: 실 Sonnet 최초채점 단독 호출 — 기존 타임아웃(30s/2048)에서 타임아웃/잘림 여부 확인")
    void realSonnetInitialEvaluationUnderOriginalTimeout() {
        EvaluationRequest request = initialEvaluationRequest();

        long start = System.currentTimeMillis();
        InitialEvaluationResponse response = invocationService.evaluateInitialAnswers(
                request,
                ScoringPromptTemplate.initialPrompt(request));
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(response.evaluations()).hasSize(4);
        assertThat(response.evaluations()).allSatisfy(e -> assertThat(e.score()).isBetween(0, 20));

        System.out.println("PHASE2_INITIAL_ELAPSED_MS=" + elapsedMs);
        System.out.println("PHASE2_INITIAL_TOTAL_SCORE=" + response.totalScore());
        System.out.println("PHASE2_INITIAL_WEAKEST=" + response.weakestQuestionId());
    }

    @Test
    @DisplayName("Phase 2: 실 Sonnet 꼬리질문생성 단독 호출 — 기존 타임아웃(30s/2048)에서 타임아웃/잘림 여부 확인")
    void realSonnetFollowUpGenerationUnderOriginalTimeout() {
        QuestionGenerationPrompt prompt = followUpPromptProvider.followUpPrompt(
                "STRICT",
                "테스터1",
                2,
                "인기 상품에서 락 대기가 길어지는 문제를 발견하고 Redis + Lua 스크립트로 전환하셨는데, "
                        + "Lua 스크립트를 쓰면 왜 별도의 락이 없어도 원자성이 보장될까요?",
                "잘 모르겠습니다. 그냥 Redis가 빠르다고 들어서 썼습니다.",
                "Redis의 빠른 성능만 언급했고 Lua 스크립트의 원자성 보장 원리를 설명하지 못했습니다.");

        long start = System.currentTimeMillis();
        FollowUpGenerationResponse response = invocationService.generateFollowUp(
                2,
                "인기 상품에서 락 대기가 길어지는 문제를 발견하고 Redis + Lua 스크립트로 전환하셨는데, "
                        + "Lua 스크립트를 쓰면 왜 별도의 락이 없어도 원자성이 보장될까요?",
                "잘 모르겠습니다. 그냥 Redis가 빠르다고 들어서 썼습니다.",
                "Redis의 빠른 성능만 언급했고 Lua 스크립트의 원자성 보장 원리를 설명하지 못했습니다.",
                new LlmPrompt(prompt.systemPrompt(), prompt.userPrompt()));
        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(response.followUpQuestion()).isNotBlank();
        assertThat(response.expectedAnswer()).isNotBlank();

        System.out.println("PHASE2_FOLLOWUP_ELAPSED_MS=" + elapsedMs);
        System.out.println("PHASE2_FOLLOWUP_QUESTION=" + response.followUpQuestion());
    }

    /** Phase 1(#2-29)에서 실제 Haiku가 생성한 이력서1(커머스/DB) 질문 4개를 그대로 재사용한다. */
    private EvaluationRequest initialEvaluationRequest() {
        return EvaluationRequest.initial(
                List.of(
                        new QuestionAnswerPair(
                                1,
                                "테스터1님의 프로젝트에서 SELECT FOR UPDATE로 재고 차감 레이스 컨디션을 막았다고 하셨는데, "
                                        + "이 방식이 정확히 어떻게 동시성 문제를 해결하는지 간단히 설명해 주실 수 있을까요?",
                                "SELECT FOR UPDATE는 조회 시점에 해당 행에 배타 락을 걸어서, 커밋 전까지 다른 트랜잭션이 "
                                        + "같은 행을 다시 조회(FOR UPDATE)하거나 수정하지 못하게 막습니다. 그래서 두 트랜잭션이 "
                                        + "동시에 같은 재고 값을 읽고 각자 차감해서 하나의 차감이 사라지는 lost update를 막을 수 있었습니다.",
                                "SELECT FOR UPDATE는 트랜잭션이 해당 행을 조회하는 시점에 배타 락을 걸어, 같은 행에 대한 "
                                        + "다른 트랜잭션의 조회나 수정을 커밋 전까지 대기시킨다. 이를 통해 lost update를 방지한다."),
                        new QuestionAnswerPair(
                                2,
                                "인기 상품에서 락 대기가 길어지는 문제를 발견하고 Redis + Lua 스크립트로 전환하셨는데, "
                                        + "Lua 스크립트를 쓰면 왜 별도의 락이 없어도 원자성이 보장될까요?",
                                "잘 모르겠습니다. 그냥 Redis가 빠르다고 들어서 썼습니다.",
                                "Lua 스크립트는 Redis 서버 안에서 단일 스레드로 원자적으로 실행되므로, 스크립트 실행 도중 "
                                        + "다른 명령이 끼어들 수 없다. 별도의 락 없이도 read-check-write 전체가 원자적으로 보장된다."),
                        new QuestionAnswerPair(
                                3,
                                "주문 목록 조회에서 offset 기반 페이지네이션에서 커서 기반으로 바꾸셨는데, "
                                        + "두 방식이 성능상 어떻게 다르고 각각 언제 사용하면 좋을지 설명해 주시겠어요?",
                                "offset 방식은 매번 앞부분 N개를 다 스캔하고 버리기 때문에 뒤 페이지로 갈수록 스캔량이 늘어서 "
                                        + "느려집니다. 커서 방식은 마지막으로 본 id를 기준으로 인덱스를 타고 바로 다음 구간으로 "
                                        + "가기 때문에 페이지 위치와 상관없이 속도가 일정합니다.",
                                "offset 방식은 뒤 페이지로 갈수록 스캔량이 늘어 느려진다. 커서 기반은 인덱스를 타고 바로 다음 "
                                        + "구간을 조회하므로 페이지 위치와 무관하게 일정한 성능을 낸다."),
                        new QuestionAnswerPair(
                                4,
                                "Spring Boot와 JPA를 사용하셨다고 했는데, N+1 문제가 발생할 수 있는 상황과 "
                                        + "이를 방지하는 방법을 설명해 주시겠어요?",
                                "N+1은 연관 엔티티를 지연 로딩할 때 컬렉션 항목마다 추가 쿼리가 나가는 문제입니다. "
                                        + "fetch join으로 한 번에 조인해서 가져오거나, hibernate.default_batch_fetch_size로 "
                                        + "배치 사이즈를 지정해서 방지했습니다.",
                                "N+1은 연관 엔티티를 지연 로딩할 때 컬렉션의 각 항목마다 추가 쿼리가 발생하는 문제다. "
                                        + "fetch join이나 배치 사이즈 설정으로 방지할 수 있다.")),
                "STRICT",
                "테스터1");
    }
}
