package com.careerdungeon.domain.progress.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 YAML에서 local 정적 fallback과 prod S3 전달 모드가 올바르게 분리되는지 검증한다. */
class BadgeImageDeliveryYamlConfigTest {

    /** 공통 기본값이 AWS 자격증명을 요구하지 않는 정적 자산 모드인지 검증한다. */
    @Test
    @DisplayName("기본 프로필은 뱃지 정적 이미지 fallback을 사용한다")
    void applicationYmlUsesStaticBadgeImagesByDefault() {
        assertUseS3("application.yml", "false");
    }

    /** 운영 프로필이 private S3 Presigned URL 모드로 기본값을 덮어쓰는지 검증한다. */
    @Test
    @DisplayName("운영 프로필은 private S3 뱃지 이미지를 사용한다")
    void applicationProdYmlUsesS3BadgeImages() {
        assertUseS3("application-prod.yml", "true");
    }

    /** 지정한 YAML의 실제 badge.images.use-s3 값을 읽어 환경 계약과 비교한다. */
    private void assertUseS3(String fileName, String expectedValue) {
        List<PropertySource<?>> sources;
        try {
            sources = new YamlPropertySourceLoader().load(fileName, new ClassPathResource(fileName));
        } catch (Exception exception) {
            throw new AssertionError(fileName + " 을(를) 읽는 데 실패했습니다.", exception);
        }

        assertThat(resolve(sources, "badge.images.use-s3")).isEqualTo(expectedValue);
    }

    /** 여러 YAML property source에서 요청한 key의 첫 값을 문자열로 반환한다. */
    private String resolve(List<PropertySource<?>> sources, String key) {
        return sources.stream()
                .map(source -> source.getProperty(key))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElseThrow(() -> new AssertionError("프로퍼티를 찾을 수 없습니다: " + key));
    }
}
