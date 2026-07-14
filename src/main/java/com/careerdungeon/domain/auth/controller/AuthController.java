package com.careerdungeon.domain.auth.controller;

import com.careerdungeon.domain.auth.dto.TokenResponse;
import com.careerdungeon.domain.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenValue = extractRefreshTokenCookie(request);
        AuthService.RefreshResult result = authService.refresh(refreshTokenValue);

        ResponseCookie newCookie = ResponseCookie.from("refreshToken", result.newRefreshTokenValue())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .maxAge(Duration.ofDays(7))
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());

        return new TokenResponse(result.accessToken());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenValue = extractRefreshTokenCookie(request);
        authService.logout(refreshTokenValue);

        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .maxAge(Duration.ZERO)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

        return Map.of("message", "로그아웃 되었습니다");
    }

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
