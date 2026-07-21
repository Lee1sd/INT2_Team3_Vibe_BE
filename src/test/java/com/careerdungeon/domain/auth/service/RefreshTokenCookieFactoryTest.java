package com.careerdungeon.domain.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이슈 #117 — refreshToken 쿠키의 Secure/SameSite가 설정값(auth.cookie.secure,
 * auth.cookie.same-site)을 그대로 따르는지, httpOnly/path/maxAge는 프로필과 무관하게
 * 기존과 동일하게 유지되는지 검증한다.
 */
class RefreshTokenCookieFactoryTest {

    @Test
    void create_withLocalConfig_producesInsecureLaxCookie() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(false, "Lax");

        ResponseCookie cookie = factory.create("refresh-value");

        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getValue()).isEqualTo("refresh-value");
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void create_withProdConfig_producesSecureNoneCookie() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(true, "None");

        ResponseCookie cookie = factory.create("refresh-value");

        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void expired_producesZeroMaxAgeCookieWithSameSecuritySettings() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(true, "None");

        ResponseCookie cookie = factory.expired();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("None");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    void constructor_withInsecureSameSiteNone_throwsIllegalState() {
        assertThatThrownBy(() -> new RefreshTokenCookieFactory(false, "None"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SameSite=None");
    }
}
