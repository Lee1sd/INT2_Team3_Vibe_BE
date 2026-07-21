package com.careerdungeon.domain.auth.controller;

import com.careerdungeon.domain.auth.dto.TokenResponse;
import com.careerdungeon.domain.auth.service.AuthService;
import com.careerdungeon.domain.auth.service.RefreshTokenCookieFactory;
import com.careerdungeon.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;

    public AuthController(AuthService authService, RefreshTokenCookieFactory cookieFactory) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenValue,
            HttpServletResponse response) {
        if (refreshTokenValue == null) {
            throw new BusinessException("AUTH_REFRESH_TOKEN_MISSING", "리프레시 토큰이 없습니다.", HttpStatus.UNAUTHORIZED);
        }
        AuthService.RefreshResult result = authService.refresh(refreshTokenValue);

        ResponseCookie newCookie = cookieFactory.create(result.newRefreshTokenValue());
        response.addHeader(HttpHeaders.SET_COOKIE, newCookie.toString());

        return new TokenResponse(result.accessToken());
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshTokenValue,
            HttpServletResponse response) {
        if (refreshTokenValue == null) {
            throw new BusinessException("AUTH_REFRESH_TOKEN_MISSING", "리프레시 토큰이 없습니다.", HttpStatus.UNAUTHORIZED);
        }
        authService.logout(refreshTokenValue);

        ResponseCookie expiredCookie = cookieFactory.expired();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

        return Map.of("message", "로그아웃 되었습니다");
    }
}