package com.careerdungeon.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * refreshToken 쿠키를 만드는 유일한 지점. Secure/SameSite는 프로필별로 다르다 — 로컬은
 * HTTP(`localhost:3000`↔`localhost:8080`)라 Secure 쿠키가 저장되지 않으므로
 * {@code Secure=false, SameSite=Lax}를, 배포 환경은 FE/BE가 다른 도메인(cross-site)이라
 * {@code Secure=true, SameSite=None}을 쓴다(이슈 #117).
 */
@Component
public class RefreshTokenCookieFactory {

    private static final String COOKIE_NAME = "refreshToken";
    private static final Duration REFRESH_TOKEN_MAX_AGE = Duration.ofDays(7);

    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookieFactory(
            @Value("${auth.cookie.secure}") boolean secure,
            @Value("${auth.cookie.same-site}") String sameSite) {
        if (!secure && "None".equalsIgnoreCase(sameSite)) {
            throw new IllegalStateException(
                    "auth.cookie.same-site=None 은 auth.cookie.secure=true와 함께 설정해야 합니다 "
                            + "(Secure 없는 SameSite=None 쿠키는 브라우저가 거부합니다).");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie create(String refreshTokenValue) {
        return build(refreshTokenValue, REFRESH_TOKEN_MAX_AGE);
    }

    public ResponseCookie expired() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .maxAge(maxAge)
                .path("/")
                .build();
    }
}
