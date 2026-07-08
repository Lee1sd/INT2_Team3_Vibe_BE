package com.careerdungeon.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * LLM과 외부 API 호출에서 {@code @Retryable}을 사용할 수 있도록 재시도 기능을 활성화한다.
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
