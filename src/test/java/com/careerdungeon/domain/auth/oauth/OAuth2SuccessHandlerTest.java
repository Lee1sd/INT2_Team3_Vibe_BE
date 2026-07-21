package com.careerdungeon.domain.auth.oauth;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.service.AuthService;
import com.careerdungeon.domain.auth.service.RefreshTokenCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 이슈 #96 — 로그인 성공 후 JSON을 그대로 응답하던 기존 동작 대신, 프론트 콜백 경로로
 * accessToken을 URL fragment에 실어 리다이렉트하는지 검증한다.
 * 이슈 #117 — refreshToken 쿠키의 Secure/SameSite가 RefreshTokenCookieFactory(설정값)를
 * 그대로 따르는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    private static final RefreshTokenCookieFactory LOCAL_COOKIE_FACTORY =
            new RefreshTokenCookieFactory(false, "Lax");

    @Test
    void onAuthenticationSuccess_redirectsToFrontendCallbackWithAccessTokenFragment() throws Exception {
        OAuth2SuccessHandler handler =
                new OAuth2SuccessHandler(authService, LOCAL_COOKIE_FACTORY, "http://localhost:3000");

        User user = new User("google-1", "test@example.com", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "google-1"));
        given(authentication.getPrincipal()).willReturn(principal);
        given(authService.login(user)).willReturn(new AuthService.LoginResult("access-token-value", "refresh-token-value"));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectUrlCaptor.capture());
        assertThat(redirectUrlCaptor.getValue())
                .isEqualTo("http://localhost:3000/oauth/callback#accessToken=access-token-value");

        verify(response).addHeader(eq("Set-Cookie"), any());
    }

    @Test
    void onAuthenticationSuccess_usesFirstOriginWhenMultipleAllowedOriginsConfigured() throws Exception {
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                authService, LOCAL_COOKIE_FACTORY, "http://localhost:3000, https://staging.example.com");

        User user = new User("google-2", "b@example.com", "김철수");
        ReflectionTestUtils.setField(user, "id", 2L);
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "google-2"));
        given(authentication.getPrincipal()).willReturn(principal);
        given(authService.login(user)).willReturn(new AuthService.LoginResult("token", "refresh"));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectUrlCaptor.capture());
        assertThat(redirectUrlCaptor.getValue()).startsWith("http://localhost:3000/oauth/callback#accessToken=");
    }

    @Test
    void onAuthenticationSuccess_withLocalCookieFactory_setsInsecureLaxCookie() throws Exception {
        OAuth2SuccessHandler handler =
                new OAuth2SuccessHandler(authService, LOCAL_COOKIE_FACTORY, "http://localhost:3000");

        User user = new User("google-3", "c@example.com", "이영희");
        ReflectionTestUtils.setField(user, "id", 3L);
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "google-3"));
        given(authentication.getPrincipal()).willReturn(principal);
        given(authService.login(user)).willReturn(new AuthService.LoginResult("token", "refresh-value"));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), cookieCaptor.capture());
        String setCookieHeader = cookieCaptor.getValue();
        assertThat(setCookieHeader).contains("refreshToken=refresh-value");
        assertThat(setCookieHeader).doesNotContain("Secure");
        assertThat(setCookieHeader).contains("SameSite=Lax");
    }

    @Test
    void onAuthenticationSuccess_withProdCookieFactory_setsSecureNoneCookie() throws Exception {
        RefreshTokenCookieFactory prodCookieFactory = new RefreshTokenCookieFactory(true, "None");
        OAuth2SuccessHandler handler =
                new OAuth2SuccessHandler(authService, prodCookieFactory, "https://career-dungeon.example.com");

        User user = new User("google-4", "d@example.com", "박민수");
        ReflectionTestUtils.setField(user, "id", 4L);
        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of("sub", "google-4"));
        given(authentication.getPrincipal()).willReturn(principal);
        given(authService.login(user)).willReturn(new AuthService.LoginResult("token", "refresh-value"));

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> cookieCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), cookieCaptor.capture());
        String setCookieHeader = cookieCaptor.getValue();
        assertThat(setCookieHeader).contains("Secure");
        assertThat(setCookieHeader).contains("SameSite=None");
    }
}
