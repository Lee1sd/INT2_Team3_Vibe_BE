package com.careerdungeon.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static org.assertj.core.api.Assertions.assertThat;

// #65 -> #66 -> #75 -> #76 로 OAuth2 콜백 baseUri/redirect-uri 불일치가 반복돼서,
// 실제 로그인 없이도 콜백 경로 패턴이 맞는지 CI에서 확인하기 위한 테스트다.
class SecurityConfigTest {

    private final AntPathRequestMatcher matcher = new AntPathRequestMatcher(SecurityConfig.OAUTH2_CALLBACK_BASE_URI);

    @Test
    @DisplayName("registrationId가 붙은 콜백 경로(/callback/google)는 baseUri 패턴에 매칭된다")
    void callbackPath_withRegistrationId_matches() {
        assertThat(matcher.matches(requestTo("/api/auth/oauth2/callback/google"))).isTrue();
    }

    @Test
    @DisplayName("다른 registrationId(github)로도 매칭된다 — 특정 provider에 하드코딩된 패턴이 아님을 확인")
    void callbackPath_withDifferentRegistrationId_matches() {
        assertThat(matcher.matches(requestTo("/api/auth/oauth2/callback/github"))).isTrue();
    }

    @Test
    @DisplayName("registrationId 없이 정확히 /callback으로만 오는 요청은 매칭되지 않는다")
    void callbackPath_withoutRegistrationId_doesNotMatch() {
        assertThat(matcher.matches(requestTo("/api/auth/oauth2/callback"))).isFalse();
    }

    // AntPathRequestMatcher는 request.getServletPath() 기준으로 매칭하는데, MockHttpServletRequest는
    // requestURI를 설정해도 servletPath를 자동으로 채워주지 않는다 — 둘 다 명시적으로 맞춰줘야 한다.
    private MockHttpServletRequest requestTo(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        return request;
    }
}
