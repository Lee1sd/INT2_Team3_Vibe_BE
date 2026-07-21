package com.careerdungeon.domain.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이슈 #117 — RefreshTokenCookieFactoryTest/OAuth2SuccessHandlerTest는 자바 코드에서 직접
 * 넘긴 리터럴 값(true/false, "Lax"/"None")만 검증할 뿐, YAML 파일에 실제로 그 값이 맞게
 * 적혀 있는지는 확인하지 않는다. prod 프로필은 DB_HOST 등 실 환경변수가 있어야 부팅되어
 * CI에서 `@ActiveProfiles("prod")`로 전체 컨텍스트를 띄울 수 없으므로, 여기서는 YAML만
 * 가볍게 파싱해서 auth.cookie.* 값이 완료 조건(local=false/Lax, prod=true/None)과
 * 일치하는지 직접 검증한다.
 */
class RefreshTokenCookieYamlConfigTest {

    @Test
    void applicationYml_기본값은_local용_InsecureLax이다() {
        assertCookieConfig("application.yml", "false", "Lax");
    }

    @Test
    void applicationProdYml_SecureAndSameSiteNone이다() {
        assertCookieConfig("application-prod.yml", "true", "None");
    }

    private void assertCookieConfig(String fileName, String expectedSecure, String expectedSameSite) {
        List<PropertySource<?>> sources;
        try {
            sources = new YamlPropertySourceLoader().load(fileName, new ClassPathResource(fileName));
        } catch (Exception e) {
            throw new AssertionError(fileName + " 을(를) 읽는 데 실패했습니다.", e);
        }

        assertThat(resolve(sources, "auth.cookie.secure")).isEqualTo(expectedSecure);
        assertThat(resolve(sources, "auth.cookie.same-site")).isEqualTo(expectedSameSite);
    }

    private String resolve(List<PropertySource<?>> sources, String key) {
        return sources.stream()
                .map(source -> source.getProperty(key))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElseThrow(() -> new AssertionError("프로퍼티를 찾을 수 없습니다: " + key));
    }
}
