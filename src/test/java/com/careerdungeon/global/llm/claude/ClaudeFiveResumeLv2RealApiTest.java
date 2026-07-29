package com.careerdungeon.global.llm.claude;

import com.careerdungeon.domain.judgment.llm.LlmEvaluationResponseAdapter;
import com.careerdungeon.domain.judgment.model.FinalJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.InitialJudgmentEvaluation;
import com.careerdungeon.domain.judgment.model.QuestionScore;
import com.careerdungeon.domain.judgment.service.JudgmentScoringService;
import com.careerdungeon.global.config.RetryConfig;
import com.careerdungeon.global.llm.LlmClient;
import com.careerdungeon.global.llm.LlmInvocationService;
import com.careerdungeon.global.llm.dto.EvaluationRequest;
import com.careerdungeon.global.llm.dto.FinalEvaluationResponse;
import com.careerdungeon.global.llm.dto.InitialEvaluationResponse;
import com.careerdungeon.global.llm.dto.PreviousEvaluationContext;
import com.careerdungeon.global.llm.dto.QuestionAnswerPair;
import com.careerdungeon.global.llm.prompt.ScoringPromptTemplate;
import com.careerdungeon.global.llm.validation.CareerReportValidator;
import com.careerdungeon.global.llm.validation.LlmResponseValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배포 환경과 같은 Claude·프롬프트·검증·루브릭 경로로 합성 이력서 5종의 Lv.2 채점을 실측한다.
 *
 * <p>각 이력서마다 적합 답변과 부적합 답변을 한 번씩 실행해 총 10건을 만든다. 질문 생성의
 * 비결정성과 UI 대기 시간을 제외하기 위해 이력서에 근거한 질문·예상답변은 고정하고,
 * 최초채점과 최종채점 및 커리어 리포트는 실제 운영 코드와 실제 Claude API를 사용한다.
 * 비용 발생을 막기 위해 {@code RUN_CLAUDE_FIVE_RESUME_LV2_TEST=true}일 때만 실행한다.
 */
@EnabledIfEnvironmentVariable(named = "RUN_CLAUDE_FIVE_RESUME_LV2_TEST", matches = "true")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        RetryConfig.class,
        LlmResponseValidator.class,
        LlmInvocationService.class,
        ClaudeFiveResumeLv2RealApiTest.TestConfig.class
})
class ClaudeFiveResumeLv2RealApiTest {

    private static final int PARALLELISM = 2;
    private static final Path RESULT_PATH =
            Path.of("build", "reports", "lv2-five-resume-real-api-results.json");

    @TestConfiguration
    static class TestConfig {

        /** 배포 서버와 같은 환경변수 계약과 확정 모델로 실제 Claude 클라이언트를 구성한다. */
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

        /** 허용된 환경변수만 검사하고 비밀값 자체는 출력하지 않는다. */
        private static String firstConfiguredEnvironment(String... names) {
            for (String name : names) {
                String value = System.getenv(name);
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            throw new IllegalStateException(String.join(" 또는 ", names) + " 환경변수가 필요합니다.");
        }

        /** 배포 모델 환경변수가 없을 때 프로젝트 확정 기본 모델을 사용한다. */
        private static String environmentOrDefault(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value;
        }
    }

    @Autowired
    LlmInvocationService invocationService;

    @Autowired
    LlmResponseValidator responseValidator;

    @Autowired
    LlmClient llmClient;

    private final LlmEvaluationResponseAdapter responseAdapter = new LlmEvaluationResponseAdapter();
    private final JudgmentScoringService scoringService =
            new JudgmentScoringService(candidates -> candidates.get(0));

    @Test
    @DisplayName("합성 이력서 5종의 적합/부적합 Lv.2 답변을 실제 운영 채점 경로로 비교한다")
    void evaluateFiveResumesWithStrongAndWeakAnswers() throws IOException {
        List<ResumeScenario> scenarios = scenarios();
        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
        List<CaseResult> results;
        try {
            List<CompletableFuture<CaseResult>> futures = new ArrayList<>();
            for (ResumeScenario scenario : scenarios) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> evaluate(scenario, AnswerQuality.STRONG), executor));
                futures.add(CompletableFuture.supplyAsync(
                        () -> evaluate(scenario, AnswerQuality.WEAK), executor));
            }
            results = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparing(CaseResult::resumeId)
                            .thenComparing(CaseResult::quality))
                    .toList();
        } finally {
            executor.shutdown();
        }

        long initialProviderCalls = countProviderCalls("evaluateInitialAnswers");
        long finalProviderCalls = countProviderCalls("evaluateFinalAnswers");
        RunResult runResult = new RunResult(
                environmentOrDefault("LLM_MODEL", "claude-haiku-4-5"),
                PARALLELISM,
                initialProviderCalls,
                finalProviderCalls,
                results);
        writeResult(runResult);

        assertThat(results).hasSize(10).allSatisfy(result -> {
            assertThat(result.scores()).hasSize(5);
            assertThat(result.totalScore()).isBetween(0, 100);
            assertThat(result.passed()).isEqualTo(result.totalScore() >= 80);
            assertThat(responseValidator.isCareerReportValid(result.overallFeedback())).isTrue();
            assertThat(result.overallFeedback())
                    .contains("🎯 총평", "✨ 이런 점이 매우 훌륭했어요")
                    .contains("🚀 합격을 확정 짓는 2%", "💡 Next Step")
                    .contains("❌ AS-IS", "⭕ TO-BE")
                    .contains(CareerReportValidator.HYPOTHETICAL_DISCLAIMER)
                    .doesNotContain(CareerReportValidator.FALLBACK_REPORT);
        });
        for (ResumeScenario scenario : scenarios) {
            CaseResult strong = findResult(results, scenario.id(), AnswerQuality.STRONG);
            CaseResult weak = findResult(results, scenario.id(), AnswerQuality.WEAK);
            assertThat(strong.totalScore())
                    .as("%s 적합 답변 점수는 부적합 답변보다 높아야 한다.", scenario.id())
                    .isGreaterThan(weak.totalScore());
        }
        assertThat(initialProviderCalls).isBetween(10L, 20L);
        assertThat(finalProviderCalls).isBetween(10L, 20L);
    }

    /** 최초·최종 LLM 원시값을 실제 judgment 루브릭으로 보정해 한 사례의 최종 결과를 만든다. */
    private CaseResult evaluate(ResumeScenario scenario, AnswerQuality quality) {
        List<QuestionAnswerPair> initialPairs = scenario.questions().stream()
                .map(question -> new QuestionAnswerPair(
                        question.turn(),
                        question.question(),
                        quality == AnswerQuality.STRONG ? question.strongAnswer() : question.weakAnswer(),
                        question.expectedAnswer()))
                .toList();
        EvaluationRequest initialRequest = EvaluationRequest.initial(initialPairs, "STRICT", "테스터");
        InitialEvaluationResponse rawInitial = invocationService.evaluateInitialAnswers(
                initialRequest,
                ScoringPromptTemplate.initialPrompt(initialRequest));
        InitialJudgmentEvaluation initial = scoringService.scoreInitial(
                responseAdapter.toRawInitial(rawInitial));

        List<PreviousEvaluationContext> previous = initial.evaluations().stream()
                .map(score -> previousContext(initialPairs, score))
                .toList();
        QuestionSpec weakest = scenario.questions().get(initial.weakestQuestionId() - 1);
        QuestionAnswerPair followUp = new QuestionAnswerPair(
                5,
                weakest.followUpQuestion(),
                quality == AnswerQuality.STRONG
                        ? weakest.strongFollowUpAnswer()
                        : weakest.weakFollowUpAnswer(),
                weakest.followUpExpectedAnswer());
        EvaluationRequest finalRequest = EvaluationRequest.finalEvaluation(
                List.of(followUp),
                previous,
                "STRICT",
                "테스터");
        FinalEvaluationResponse rawFinal = invocationService.evaluateFinalAnswers(
                finalRequest,
                ScoringPromptTemplate.finalPrompt(finalRequest));
        FinalJudgmentEvaluation finalEvaluation = scoringService.scoreFinal(
                initial,
                responseAdapter.toRawFinal(rawFinal));

        return new CaseResult(
                scenario.id(),
                scenario.title(),
                quality,
                finalEvaluation.evaluations(),
                finalEvaluation.totalScore(),
                finalEvaluation.passed(),
                initial.weakestQuestionId(),
                finalEvaluation.overallFeedback());
    }

    /** 최초 확정 점수와 피드백을 최종 리포트의 읽기 전용 컨텍스트로 변환한다. */
    private PreviousEvaluationContext previousContext(
            List<QuestionAnswerPair> pairs,
            QuestionScore score) {
        QuestionAnswerPair pair = pairs.get(score.questionId() - 1);
        return new PreviousEvaluationContext(
                score.questionId(),
                pair.questionText(),
                pair.userAnswer(),
                score.score(),
                score.feedback());
    }

    /** 실행 결과에서 이력서와 답변 품질이 일치하는 한 건을 찾는다. */
    private CaseResult findResult(
            List<CaseResult> results,
            String resumeId,
            AnswerQuality quality) {
        return results.stream()
                .filter(result -> result.resumeId().equals(resumeId) && result.quality() == quality)
                .findFirst()
                .orElseThrow();
    }

    /** 실제 벤더 spy에서 단계별 재시도를 포함한 호출 횟수를 집계한다. */
    private long countProviderCalls(String methodName) {
        return Mockito.mockingDetails(llmClient).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(methodName)
                        && invocation.getArguments().length == 2)
                .count();
    }

    /** 분석 가능한 JSON 결과 파일을 UTF-8로 저장한다. */
    private void writeResult(RunResult result) throws IOException {
        Files.createDirectories(RESULT_PATH.getParent());
        new ObjectMapper().findAndRegisterModules()
                .writerWithDefaultPrettyPrinter()
                .writeValue(RESULT_PATH.toFile(), result);
    }

    /** 환경변수 미설정 시 배포 기본 모델을 선택한다. */
    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** 이력서 다섯 종류와 각 이력서에 근거한 네 문항을 구성한다. */
    private List<ResumeScenario> scenarios() {
        return List.of(
                commerceScenario(),
                paymentScenario(),
                logisticsScenario(),
                dataPlatformScenario(),
                cloudPlatformScenario());
    }

    /** 커머스 백엔드의 DB·동시성·캐시·이벤트 경험을 검증한다. */
    private ResumeScenario commerceScenario() {
        return scenario("R1", "커머스 백엔드",
                q(1, "주문 목록 N+1을 어떻게 진단하고 DTO projection과 fetch join을 나눠 적용했습니까?",
                        "쿼리 로그와 Hibernate 통계로 반복 쿼리를 확인하고 목록의 페이징 요구와 상세의 연관 조회 요구를 "
                                + "구분한다. 전후 쿼리 수와 지연 시간, 컬렉션 fetch join의 페이징 한계를 검증한다.",
                        "요청당 31개 쿼리를 traceId로 묶어 확인했습니다. 목록은 일부 컬럼과 페이징이 핵심이라 DTO "
                                + "projection으로 4개 쿼리까지 줄였고, 단건 상세는 fetch join을 사용했습니다. 컬렉션 "
                                + "fetch join은 페이징 중복 위험이 있어 목록에는 쓰지 않았고 p95가 420ms에서 150ms로 "
                                + "줄었는지와 중복·누락이 없는지 함께 검증했습니다.",
                        "N+1은 느려서 생기는 문제입니다. fetch join을 쓰면 해결됩니다.",
                        "fetch join을 목록 페이징에 적용하지 않은 구체적인 이유와 대안은 무엇입니까?",
                        "컬렉션 fetch join의 row 증폭과 메모리 페이징 위험을 설명하고 DTO projection, batch size, "
                                + "ID 선조회 후 상세 조회 같은 대안을 데이터 크기와 조회 목적에 따라 비교한다.",
                        "컬렉션 fetch join은 Cartesian row 증가와 in-memory 페이징 위험이 있습니다. 그래서 목록은 "
                                + "DTO projection을 쓰고, 연관 엔티티가 필요하면 ID 페이지를 먼저 구한 뒤 두 번째 쿼리로 "
                                + "조회하거나 batch size를 사용합니다. 쿼리 수와 메모리, 페이지 정확성을 비교합니다.",
                        "fetch join이 더 빠르니까 목록에도 쓰면 됩니다."),
                q(2, "재고 차감에서 비관적 락과 낙관적 락을 어떤 기준으로 선택했습니까?",
                        "경합률과 실패 비용을 기준으로 비관적·낙관적 락을 비교하고 제한 재시도, 타임아웃, 교착 방어와 "
                                + "중복 차감 검증을 설명한다.",
                        "한정 판매처럼 같은 SKU 경합이 높은 구간은 비관적 락으로 직렬화했고, 충돌률 1% 미만의 일반 "
                                + "상품은 낙관적 락과 지터 백오프 3회로 처리했습니다. 락 대기와 p99, 재시도율을 측정하고 "
                                + "부하 테스트에서 중복 차감 0건을 확인했습니다.",
                        "동시성 문제는 synchronized로 처리했습니다. 자세한 기준은 없습니다.",
                        "낙관적 락 재시도를 세 번으로 제한한 운영 근거는 무엇입니까?",
                        "재시도 성공률과 지연·DB 부하를 근거로 상한을 정하고 소진 시 사용자 응답과 관측 지표를 설명한다.",
                        "세 번 이후 성공률이 급감하고 p99와 DB 부하가 증가해 상한을 정했습니다. 50ms·100ms 지터 "
                                + "백오프 후에도 실패하면 재시도 가능 응답을 반환하고 충돌률과 소진율을 경보로 봅니다.",
                        "세 번이면 보통 충분해서 정했습니다."),
                q(3, "Redis cache-aside의 정합성과 Redis 장애 폴백을 어떻게 설계했습니까?",
                        "DB 커밋 후 무효화, TTL과 이벤트 유실 방어, stampede 제한, DB 폴백과 관측 지표를 설명한다.",
                        "DB 커밋 후 캐시를 삭제하고 변경 이벤트 무효화와 TTL 5분을 함께 사용했습니다. 삭제 실패는 "
                                + "재시도 큐로 보내고 Redis 장애 시 짧은 타임아웃과 circuit breaker로 DB에 우회하되 "
                                + "single-flight와 rate limit으로 DB 폭주를 막았습니다.",
                        "캐시는 TTL을 주면 알아서 맞습니다. 장애가 나면 다시 켜면 됩니다.",
                        "캐시 삭제 이벤트가 유실됐을 때 오래된 값이 영구히 남지 않는 이유는 무엇입니까?",
                        "TTL을 최종 안전망으로 두고 유실 탐지·재처리 및 불일치 지표를 설명한다.",
                        "이벤트가 유실돼도 TTL 5분이 stale 값의 최대 수명을 제한합니다. 소비 지연과 삭제 실패를 "
                                + "모니터링하고 재처리하며, 중요 조회는 버전 비교로 불일치를 탐지합니다.",
                        "사용자가 새로고침하면 새 값이 나옵니다."),
                q(4, "주문 DB 저장과 Kafka 발행의 이중 쓰기를 어떻게 해결했습니까?",
                        "로컬 트랜잭션 outbox, at-least-once 중복 가능성, 소비자 멱등성과 지연·DLQ 모니터링을 설명한다.",
                        "주문과 outbox를 같은 DB 트랜잭션에 저장하고 릴레이가 Kafka로 발행합니다. 상태 저장 전 장애로 "
                                + "중복 발행될 수 있어 eventId 고유 키로 소비자를 멱등하게 만들고 미발행 지연과 DLQ를 "
                                + "모니터링했습니다.",
                        "DB 저장 후 Kafka를 호출하고 실패하면 로그를 남겼습니다.",
                        "발행 성공 후 outbox 완료 표시 전에 장애가 나면 어떻게 됩니까?",
                        "재발행으로 인한 중복과 소비자 멱등 키, 재처리·관측 방식을 설명한다.",
                        "재시작 후 같은 outbox가 재발행되므로 at-least-once를 전제로 합니다. 소비자 처리 테이블의 "
                                + "eventId UNIQUE로 비즈니스 변경을 한 번만 적용하고 릴레이 지연과 반복 실패를 경보로 봅니다.",
                        "Kafka가 중복을 자동으로 제거하므로 문제없습니다."));
    }

    /** 결제 플랫폼의 멱등성·보상·배치·보안 판단을 검증한다. */
    private ResumeScenario paymentScenario() {
        return scenario("R2", "결제 플랫폼",
                q(1, "결제 승인 API의 idempotency key를 어떻게 검증하고 재사용합니까?",
                        "키와 요청 해시, 처리 상태·결과를 원자적으로 저장하고 동일 키의 다른 요청을 거부한다. 동시 요청과 "
                                + "실패 후 재시도를 고려한다.",
                        "키와 정규화한 요청 해시를 UNIQUE로 저장합니다. 같은 키·같은 요청은 진행 중 상태를 기다리거나 "
                                + "기존 승인 결과를 반환하고, 같은 키에 다른 금액·주문이면 409로 거부합니다. 승인 상태 "
                                + "변경과 결과 저장을 같은 트랜잭션으로 묶고 동시 중복 승인 테스트를 수행했습니다.",
                        "요청마다 UUID를 만들면 중복 결제가 발생하지 않습니다.",
                        "동일 키 요청 두 개가 동시에 최초 진입하면 어떻게 한 번만 승인합니까?",
                        "DB UNIQUE 또는 원자적 선점으로 승자 하나만 외부 승인을 수행하고 패자는 결과를 재사용한다.",
                        "idempotency key UNIQUE 삽입으로 한 요청만 PENDING을 선점합니다. 패자는 행 상태를 조회해 완료 "
                                + "결과를 재사용하며, 오래된 PENDING은 만료 후 상태 조회와 보상 절차를 거칩니다.",
                        "먼저 도착한 요청이 처리될 것이라 가정합니다."),
                q(2, "PG 승인과 내부 주문 상태 사이 부분 실패를 어떻게 복구합니까?",
                        "분산 원자성의 한계를 인정하고 outbox·보상·재conciliation 및 미정산 지표를 설명한다.",
                        "외부 승인과 DB를 단일 트랜잭션으로 묶을 수 없어 승인 식별자를 기록하고 outbox로 후속 상태를 "
                                + "전파합니다. 내부 저장 실패 시 PG 상태 조회 후 취소 보상을 실행하고, 보상 실패는 재시도와 "
                                + "수동 처리 큐로 보내 미정산 금액과 건수를 경보로 관리합니다.",
                        "둘 중 하나가 실패하면 전체 트랜잭션을 롤백합니다.",
                        "보상 취소 요청도 타임아웃으로 결과를 모르면 어떻게 판단합니까?",
                        "멱등 키로 재요청하고 PG 상태 조회로 결과를 확정하며 불명 상태를 운영 큐로 격리한다.",
                        "원승인 ID를 멱등 키로 취소를 재요청하고 PG 조회 API로 실제 상태를 확인합니다. 확정할 수 없는 "
                                + "건은 UNKNOWN으로 격리해 자동 재조회 후 수동 검토하며 금액 합계를 대조합니다.",
                        "취소 요청을 계속 반복합니다."),
                q(3, "정산 배치를 체크포인트부터 안전하게 재개하는 방법은 무엇입니까?",
                        "기준 시각, 고유 커서, 멱등 산출물, 청크 트랜잭션과 재실행 검증을 설명한다.",
                        "실행 기준 시각을 고정하고 settlementId 커서로 1,000건씩 처리합니다. 건별 정산 결과는 UNIQUE로 "
                                + "멱등 저장하고 청크 커밋 뒤 체크포인트를 갱신합니다. 장애 후 마지막 성공 커서부터 재개하며 "
                                + "입력·출력 금액 합계와 중복 키를 검증합니다.",
                        "실패한 배치는 처음부터 다시 실행합니다.",
                        "체크포인트 저장 직전 장애가 나도 중복 정산되지 않는 이유는 무엇입니까?",
                        "산출물 고유 키와 UPSERT/중복 무시로 재처리를 멱등하게 하고 합계 검증을 수행한다.",
                        "체크포인트보다 결과가 먼저 저장되므로 같은 청크가 재실행될 수 있습니다. 정산 대상+회차 UNIQUE로 "
                                + "이미 처리된 결과를 재사용하고, 전후 금액 합계와 중복 건수를 검증합니다.",
                        "체크포인트가 있으니 중복되지 않습니다."),
                q(4, "결제 민감정보와 운영 로그를 어떤 경계로 보호합니까?",
                        "비저장·토큰화, 로그 마스킹, 전송 암호화, 최소 권한과 감사 로그 및 비밀 회전을 설명한다.",
                        "카드 원문은 저장하지 않고 PG 토큰만 보관합니다. 요청·예외 로그에 PAN 패턴 마스킹을 적용하고 "
                                + "TLS, KMS 암호화, 서비스별 최소 권한 IAM과 접근 감사 로그를 사용합니다. 마스킹 회귀 "
                                + "테스트와 비밀 회전 훈련도 수행합니다.",
                        "DB 비밀번호를 복잡하게 만들면 안전합니다.",
                        "예외 객체가 요청 전문을 포함해 로그로 출력되는 경로는 어떻게 막습니까?",
                        "구조화 로그 allowlist, 중앙 필터, 예외 메시지 정제와 회귀 테스트를 설명한다.",
                        "로그 필드는 allowlist로 만들고 공통 필터에서 PAN·토큰을 마스킹합니다. 외부 예외 원문은 내부 "
                                + "보안 채널에만 제한하고 사용자 로그에는 오류 코드만 남기며 샘플 민감정보로 CI 검사를 합니다.",
                        "운영자가 로그를 조심해서 확인합니다."));
    }

    /** 물류 이벤트 시스템의 순서·멱등·외부 장애·검색 정합성을 검증한다. */
    private ResumeScenario logisticsScenario() {
        return scenario("R3", "물류 이벤트 시스템",
                q(1, "배송 이벤트의 순서를 Kafka에서 어떤 범위로 보장합니까?",
                        "shipmentId 파티션 키로 단일 배송 순서를 보장하고 파티션 간 전역 순서는 보장하지 않음을 설명한다.",
                        "shipmentId를 파티션 키로 사용해 한 배송 건의 이벤트가 같은 파티션에서 순서대로 처리되게 했습니다. "
                                + "파티션 간 전역 순서는 요구하지 않고 상태 전이 버전으로 역행 이벤트를 거부합니다. 파티션 "
                                + "증설 시 키 분포와 hot partition을 측정합니다.",
                        "Kafka는 메시지 순서를 항상 보장합니다.",
                        "늦게 도착한 과거 상태 이벤트가 현재 상태를 되돌리지 않게 하는 방법은 무엇입니까?",
                        "상태 버전·발생 시각과 허용 상태 전이 규칙을 원자적으로 검증한다.",
                        "이벤트 sequence와 현재 version을 비교하고 기대 version일 때만 상태 전이를 반영합니다. 이미 지난 "
                                + "이벤트는 감사 저장 후 무시하고 불일치율을 모니터링합니다.",
                        "도착한 순서대로 덮어씁니다."),
                q(2, "at-least-once 소비자에서 중복 처리를 어떻게 막습니까?",
                        "eventId UNIQUE와 비즈니스 변경을 같은 트랜잭션으로 묶고 재처리·DLQ를 설명한다.",
                        "eventId 처리 이력과 배송 상태 변경을 같은 DB 트랜잭션으로 저장합니다. UNIQUE 충돌이면 이미 처리된 "
                                + "이벤트로 간주해 ACK하고, poison message는 제한 재시도 후 DLQ로 보내 원인과 재처리 이력을 "
                                + "남깁니다.",
                        "consumer auto commit을 켜면 중복이 없습니다.",
                        "DB 커밋 후 Kafka offset 커밋 전에 장애가 나면 어떻게 됩니까?",
                        "재수신되므로 DB 멱등 키로 변경을 한 번만 적용하고 offset을 이후 커밋한다.",
                        "재시작 후 이벤트가 다시 오지만 eventId UNIQUE가 두 번째 변경을 막습니다. DB 성공 뒤 offset을 "
                                + "커밋하고 재전달·중복률을 관측합니다.",
                        "offset이 자동으로 복구하므로 그대로 처리합니다."),
                q(3, "외부 택배사 장애가 내부 스레드 고갈로 번지지 않게 어떻게 방어합니까?",
                        "짧은 타임아웃, 제한 재시도·지터, circuit breaker, 격리와 DLQ·지표를 설명한다.",
                        "연결·응답 타임아웃을 2초로 제한하고 지수 백오프 최대 3회, circuit breaker와 별도 bulkhead를 "
                                + "사용했습니다. 실패 요청은 DLQ로 격리하고 택배사별 오류율·대기 큐·스레드 사용률을 경보로 "
                                + "관리했습니다.",
                        "응답이 올 때까지 기다리고 실패하면 다시 호출합니다.",
                        "장애 중 재시도가 오히려 부하를 키우지 않게 하는 기준은 무엇입니까?",
                        "재시도 가능한 오류만 분류하고 지터·상한·회로 차단 및 Retry-After를 적용한다.",
                        "타임아웃·5xx만 재시도하고 4xx는 제외합니다. 지터 백오프와 최대 횟수, circuit breaker를 두며 "
                                + "상대 Retry-After와 내부 큐 길이를 반영해 호출을 줄입니다.",
                        "재시도를 많이 하면 결국 성공합니다."),
                q(4, "Elasticsearch와 원본 DB의 최종 일관성을 어떻게 사용자에게 노출합니까?",
                        "검색은 지연을 허용하고 상세는 DB를 사용하며 인덱스 지연·재색인·누락 검증을 설명한다.",
                        "목록 검색은 비동기 인덱스의 수초 지연을 허용하지만 상세 상태는 DB를 조회합니다. 변경 이벤트에 "
                                + "version을 넣어 오래된 갱신을 거부하고 lag, 누락 건수, DLQ를 관측하며 주기적으로 DB와 "
                                + "대조해 재색인합니다.",
                        "Elasticsearch를 원본으로 사용하면 빠르고 문제없습니다.",
                        "인덱스 갱신 이벤트가 유실된 문서를 어떻게 발견합니까?",
                        "DB와 인덱스의 version·건수 체크섬 대조 및 재색인 작업을 설명한다.",
                        "기간별 DB 변경 version과 인덱스 version을 대조하고 건수·체크섬 불일치를 찾습니다. 누락 ID는 "
                                + "멱등 재색인 큐로 보내며 lag와 복구 시간을 기록합니다.",
                        "사용자가 검색을 다시 하면 갱신됩니다."));
    }

    /** 데이터 플랫폼의 배치·스트림·멱등성·품질 방어를 검증한다. */
    private ResumeScenario dataPlatformScenario() {
        return scenario("R4", "데이터 플랫폼",
                q(1, "Spark 집계 시간을 95분에서 37분으로 줄인 근거와 부작용은 무엇입니까?",
                        "파티셔닝·predicate pushdown·small file 병합의 효과를 stage 지표로 검증하고 skew·재처리 비용을 본다.",
                        "날짜·서비스 파티션으로 읽기 범위를 줄이고 Parquet predicate pushdown과 작은 파일 compaction을 "
                                + "적용했습니다. Spark UI에서 scan bytes, shuffle read/write, task 편차를 비교해 95분에서 "
                                + "37분으로 줄였습니다. 과도한 파티션과 skew 위험 때문에 파일 크기와 재처리 범위도 측정했습니다.",
                        "서버 사양을 높여서 빨라졌습니다.",
                        "특정 서비스 키에 데이터가 몰리는 skew는 어떻게 진단하고 완화합니까?",
                        "task 시간·shuffle 분포로 skew를 찾고 salting, 사전 집계, AQE를 비교한다.",
                        "Spark UI에서 일부 task 시간과 shuffle read가 중앙값보다 크게 튀는지 봅니다. hot key는 salting 후 "
                                + "재집계하거나 사전 집계하고 AQE skew join을 적용하며 결과 정확성과 추가 비용을 비교합니다.",
                        "worker를 더 늘리면 됩니다."),
                q(2, "event-time window와 grace period를 어떤 기준으로 정했습니까?",
                        "지연 분포와 정확도·지연 트레이드오프로 window/grace를 정하고 late event 보정을 설명한다.",
                        "이벤트 도착 지연 p99가 약 70초라 5분 event-time window와 2분 grace를 사용했습니다. grace 안의 "
                                + "이벤트는 실시간 결과를 수정하고 더 늦은 건 별도 토픽으로 보내 배치 보정합니다. 완결 지연과 "
                                + "정확도, late 비율을 함께 측정했습니다.",
                        "5분이 보기 좋아서 정했습니다.",
                        "grace를 늘릴수록 항상 정확도가 좋아지는 선택입니까?",
                        "상태 저장 비용과 결과 확정 지연이 커지는 반대 비용을 설명한다.",
                        "늦은 이벤트 반영률은 높아지지만 state store 크기와 결과 확정 지연, 재처리 비용이 늘어납니다. "
                                + "지연 분포와 업무 허용 지연을 기준으로 정하고 초과분은 배치 보정합니다.",
                        "정확해지므로 최대한 길게 둡니다."),
                q(3, "Airflow 재실행에서 불완전 산출물 노출을 어떻게 막습니까?",
                        "실행 키 멱등성, 임시 경로, 원자적 교체와 성공 마커·체크포인트를 설명한다.",
                        "execution date와 데이터 version을 출력 키로 사용하고 임시 경로에 쓴 뒤 검증 성공 시 원자적으로 "
                                + "최종 경로를 교체하고 SUCCESS 마커를 남깁니다. 같은 실행 재시도는 기존 성공 결과를 재사용하고 "
                                + "중간 파일은 소비자가 읽지 않습니다.",
                        "실패하면 파일을 지우고 다시 실행합니다.",
                        "성공 마커 기록 직전에 장애가 나면 어떤 상태이며 어떻게 복구합니까?",
                        "최종 산출물 검증과 마커 생성의 재실행 멱등성, 임시 파일 정리를 설명한다.",
                        "최종 파일은 있을 수 있지만 마커가 없어 소비되지 않습니다. 재실행 시 checksum과 schema를 검증한 뒤 "
                                + "같은 마커를 멱등 생성하고 오래된 임시 경로를 정리합니다.",
                        "수동으로 파일을 확인합니다."),
                q(4, "데이터 품질 검사를 배포 게이트로 사용할 때 오탐과 누락을 어떻게 관리합니까?",
                        "null·중복·건수·스키마 기준의 기준선과 허용치, 격리·경보 및 추세 관측을 설명한다.",
                        "필수 키 null 0%, 고유 키 중복 0건, 입력 대비 출력 건수 허용 편차와 backward-compatible schema를 "
                                + "게이트로 둡니다. 계절 변동 지표는 고정값 대신 기준선 범위를 쓰고 실패 데이터는 격리해 샘플과 "
                                + "분포를 확인한 뒤 승인 또는 롤백합니다.",
                        "null이 있으면 모두 삭제합니다.",
                        "입력 건수가 정상적으로 급증했을 때 고정 임계치 오탐을 어떻게 줄입니까?",
                        "비율·계절 기준선과 다중 지표를 사용하고 승인 가능한 예외 절차를 둔다.",
                        "절대 건수보다 null·중복 비율과 요일별 이동 기준선을 사용합니다. schema 변경·트래픽 지표와 함께 "
                                + "판단하고 사유·기간이 기록된 일시 예외만 허용합니다.",
                        "임계치를 더 크게 올립니다."));
    }

    /** 클라우드 플랫폼의 배포·IaC·오토스케일·SLO 운영 판단을 검증한다. */
    private ResumeScenario cloudPlatformScenario() {
        return scenario("R5", "클라우드 플랫폼",
                q(1, "canary 10%→50%→100% 배포의 자동 롤백 기준을 어떻게 정했습니까?",
                        "기준군 대비 5xx·p95·핵심 비즈니스 지표와 최소 표본·관찰 시간을 사용한다.",
                        "기준 버전 대비 5xx가 1%p 증가하거나 p95가 20% 이상 악화된 상태가 5분 지속되면 롤백했습니다. "
                                + "최소 요청 표본과 결제 성공률 같은 핵심 지표도 함께 보고 10%, 50% 단계마다 관찰 시간을 "
                                + "둬 순간 노이즈로 롤백하지 않게 했습니다.",
                        "오류가 보이면 운영자가 수동 롤백합니다.",
                        "트래픽 10% 단계의 표본이 적을 때 잘못된 승격을 어떻게 막습니까?",
                        "최소 표본·최소 관찰 시간과 절대 오류 건수, 합성 트래픽을 설명한다.",
                        "최소 요청 수와 10분 관찰 시간을 모두 충족해야 승격합니다. 저트래픽 서비스는 합성 요청과 절대 오류 "
                                + "건수를 함께 보고 표본이 부족하면 자동 승격하지 않습니다.",
                        "10%에서 문제없으면 바로 올립니다."),
                q(2, "Terraform state와 실행 권한을 어떻게 보호합니까?",
                        "원격 암호화 state, 잠금, plan 리뷰, 분리 실행 역할과 비밀 비저장을 설명한다.",
                        "암호화된 원격 state와 잠금을 사용하고 CI의 전용 최소 권한 역할만 apply할 수 있게 했습니다. PR에는 "
                                + "plan을 첨부해 리뷰하고 state에 비밀 원문이 들어가지 않게 외부 secret reference를 씁니다. "
                                + "동시 apply와 drift를 정기 검사합니다.",
                        "state 파일을 Git에 올려 팀원이 공유합니다.",
                        "긴급 콘솔 변경으로 drift가 생기면 어떻게 탐지하고 복구합니까?",
                        "주기적 plan, import 또는 코드 반영, 임의 변경 감사와 재발 방지를 설명한다.",
                        "정기 plan의 non-empty diff와 CloudTrail을 경보로 봅니다. 승인된 긴급 변경은 import하거나 코드에 "
                                + "반영한 뒤 plan을 0으로 만들고, 불필요한 변경은 코드 기준으로 되돌립니다.",
                        "다음 배포 때 Terraform이 알아서 맞춥니다."),
                q(3, "CPU 외 queue depth 기반 HPA를 추가한 이유와 안정화 방법은 무엇입니까?",
                        "업무량 선행 지표, custom metric 지연·실패 폴백, 최소 replica와 stabilization을 설명한다.",
                        "비동기 소비자는 CPU가 낮아도 backlog가 쌓일 수 있어 queue depth와 처리율을 사용했습니다. metric "
                                + "지연 시 CPU 기준으로 폴백하고 최소 replica, scale-up 상한, 5분 scale-down stabilization으로 "
                                + "진동을 줄였습니다. backlog 처리 시간과 비용을 비교했습니다.",
                        "CPU가 높으면 pod를 많이 늘렸습니다.",
                        "custom metric 수집이 끊겼을 때 잘못된 scale-down을 어떻게 막습니까?",
                        "metric freshness 검증, 보수적 폴백과 최소 replica·경보를 설명한다.",
                        "metric timestamp가 허용 시간을 넘으면 해당 신호를 무효로 보고 CPU와 최소 replica 기준을 유지합니다. "
                                + "unknown 상태에서는 scale-down을 금지하고 수집 실패를 경보로 보냅니다.",
                        "다음 수집 주기까지 기다립니다."),
                q(4, "SLO burn-rate 경보가 단순 오류율 임계치보다 나은 이유는 무엇입니까?",
                        "오류 예산 소진 속도와 짧은·긴 창 조합으로 급성·만성 장애를 구분한다.",
                        "동일한 1% 오류라도 SLO와 남은 오류 예산에 따라 위험도가 다릅니다. 5분·1시간의 빠른 소진 경보와 "
                                + "6시간·3일의 느린 소진 경보를 조합해 급성 장애와 만성 열화를 구분하고, traceId로 로그·메트릭·"
                                + "트레이스를 연결해 원인을 찾았습니다.",
                        "오류가 5%를 넘으면 알림을 보냅니다.",
                        "짧은 창 하나만 쓰면 어떤 운영 문제가 생깁니까?",
                        "순간 노이즈 오탐과 완만한 장기 소진 누락을 설명하고 다중 창을 제시한다.",
                        "짧은 창만 보면 순간 spike에 자주 깨고 낮지만 지속되는 오류 예산 소진을 놓칩니다. 짧은 창과 긴 창이 "
                                + "동시에 임계치를 넘는 조건을 사용해 긴급도와 지속성을 함께 판단합니다.",
                        "알림이 조금 많아지는 것 외에는 문제없습니다."));
    }

    /** 네 문항을 가진 이력서 시나리오를 생성한다. */
    private ResumeScenario scenario(String id, String title, QuestionSpec... questions) {
        return new ResumeScenario(id, title, List.of(questions));
    }

    /** 최초 질문과 대응 꼬리질문의 적합·부적합 답변 묶음을 생성한다. */
    private QuestionSpec q(
            int turn,
            String question,
            String expectedAnswer,
            String strongAnswer,
            String weakAnswer,
            String followUpQuestion,
            String followUpExpectedAnswer,
            String strongFollowUpAnswer,
            String weakFollowUpAnswer) {
        return new QuestionSpec(
                turn,
                question,
                expectedAnswer,
                strongAnswer,
                weakAnswer,
                followUpQuestion,
                followUpExpectedAnswer,
                strongFollowUpAnswer,
                weakFollowUpAnswer);
    }

    private enum AnswerQuality {
        STRONG,
        WEAK
    }

    private record ResumeScenario(
            String id,
            String title,
            List<QuestionSpec> questions
    ) {
    }

    private record QuestionSpec(
            int turn,
            String question,
            String expectedAnswer,
            String strongAnswer,
            String weakAnswer,
            String followUpQuestion,
            String followUpExpectedAnswer,
            String strongFollowUpAnswer,
            String weakFollowUpAnswer
    ) {
    }

    private record CaseResult(
            String resumeId,
            String resumeTitle,
            AnswerQuality quality,
            List<QuestionScore> scores,
            int totalScore,
            boolean passed,
            int weakestQuestionId,
            String overallFeedback
    ) {
    }

    private record RunResult(
            String model,
            int parallelism,
            long initialProviderCalls,
            long finalProviderCalls,
            List<CaseResult> cases
    ) {
    }
}
