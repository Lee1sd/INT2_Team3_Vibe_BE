package com.careerdungeon.global.llm.claude;

import com.careerdungeon.global.llm.dto.LlmPrompt;
import com.careerdungeon.global.llm.exception.LlmProviderConfigException;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ClaudeLlmClientHttpFailureTest {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final LlmPrompt PROMPT = new LlmPrompt("system", "user");

    @Test
    void generateQuestions_whenUnauthorized_throwsNonRetryableProviderConfigException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("raw provider body must not matter"));
        ClaudeLlmClient sut = client(builder);

        assertThatThrownBy(() -> sut.generateQuestions(null, PROMPT))
                .isInstanceOfSatisfying(LlmProviderConfigException.class, e ->
                        assertThat(e.statusCode()).isEqualTo(401));
        server.verify();
    }

    @Test
    void generateQuestions_whenBadRequest_throwsNonRetryableProviderConfigException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("bad request body"));
        ClaudeLlmClient sut = client(builder);

        assertThatThrownBy(() -> sut.generateQuestions(null, PROMPT))
                .isInstanceOfSatisfying(LlmProviderConfigException.class, e ->
                        assertThat(e.statusCode()).isEqualTo(400));
        server.verify();
    }

    @Test
    void generateQuestions_whenRateLimited_throwsRetryableSchemaException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("rate limit body"));
        ClaudeLlmClient sut = client(builder);

        assertThatThrownBy(() -> sut.generateQuestions(null, PROMPT))
                .isInstanceOfSatisfying(LlmSchemaValidationException.class, e ->
                        assertThat(e.statusCode()).isEqualTo(429));
        server.verify();
    }

    @Test
    void generateQuestions_whenRequestTimeout_throwsRetryableSchemaException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT).body("request timeout body"));
        ClaudeLlmClient sut = client(builder);

        assertThatThrownBy(() -> sut.generateQuestions(null, PROMPT))
                .isInstanceOfSatisfying(LlmSchemaValidationException.class, e ->
                        assertThat(e.statusCode()).isEqualTo(408));
        server.verify();
    }

    @Test
    void generateQuestions_whenConflict_throwsRetryableSchemaException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("conflict body"));
        ClaudeLlmClient sut = client(builder);

        assertThatThrownBy(() -> sut.generateQuestions(null, PROMPT))
                .isInstanceOfSatisfying(LlmSchemaValidationException.class, e ->
                        assertThat(e.statusCode()).isEqualTo(409));
        server.verify();
    }

    @Test
    void generateQuestions_whenServerError_throwsRetryableSchemaException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/v1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("server error body"));
        ClaudeLlmClient sut = client(builder);

        assertThatThrownBy(() -> sut.generateQuestions(null, PROMPT))
                .isInstanceOfSatisfying(LlmSchemaValidationException.class, e ->
                        assertThat(e.statusCode()).isEqualTo(500));
        server.verify();
    }

    @Test
    void generateQuestions_whenTimeout_throwsRetryableSchemaException() {
        ClientHttpRequestFactory timeoutFactory = (uri, httpMethod) -> {
            throw new SocketTimeoutException("read timed out");
        };
        ClaudeLlmClient sut = new ClaudeLlmClient(
                RestClient.builder(),
                new ObjectMapper(),
                "claude-haiku-4-5",
                "test-api-key",
                BASE_URL,
                "2023-06-01",
                2048,
                timeoutFactory);

        assertThatThrownBy(() -> sut.generateQuestions(null, PROMPT))
                .isInstanceOf(LlmSchemaValidationException.class)
                .hasMessage("Claude API request failed");
    }

    private ClaudeLlmClient client(RestClient.Builder builder) {
        return new ClaudeLlmClient(
                builder,
                new ObjectMapper(),
                "claude-haiku-4-5",
                "test-api-key",
                BASE_URL,
                "2023-06-01",
                2048,
                null);
    }
}
