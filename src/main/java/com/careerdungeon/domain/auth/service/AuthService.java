package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.entity.RefreshToken;
import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.RefreshTokenRepository;
import com.careerdungeon.global.exception.BusinessException;
import com.careerdungeon.global.security.JwtProvider;
import com.careerdungeon.global.util.TokenHashUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    public AuthService(RefreshTokenRepository refreshTokenRepository, JwtProvider jwtProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
    }

    public LoginResult login(User user) {
        refreshTokenRepository.deleteByUser(user);
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshTokenValue = UUID.randomUUID().toString();
        String tokenHash = TokenHashUtil.hash(refreshTokenValue);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        refreshTokenRepository.save(RefreshToken.issue(user, tokenHash, expiresAt));
        return new LoginResult(accessToken, refreshTokenValue);
    }

    public RefreshResult refresh(String refreshTokenValue) {
        String tokenHash = TokenHashUtil.hash(refreshTokenValue);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(
                        "AUTH_REFRESH_TOKEN_INVALID", "유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED));

        if (!refreshToken.isValid()) {
            throw new BusinessException(
                    "AUTH_REFRESH_TOKEN_EXPIRED", "만료되었거나 취소된 리프레시 토큰입니다. 다시 로그인해 주세요.", HttpStatus.UNAUTHORIZED);
        }

        refreshToken.revoke();

        String newRefreshTokenValue = UUID.randomUUID().toString();
        String newTokenHash = TokenHashUtil.hash(newRefreshTokenValue);
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        refreshTokenRepository.save(RefreshToken.issue(refreshToken.getUser(), newTokenHash, expiresAt));

        String newAccessToken = jwtProvider.generateAccessToken(refreshToken.getUser().getId());
        return new RefreshResult(newAccessToken, newRefreshTokenValue);
    }

    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null) return;
        String tokenHash = TokenHashUtil.hash(refreshTokenValue);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(RefreshToken::revoke);
    }

    public record LoginResult(String accessToken, String refreshTokenValue) {}

    public record RefreshResult(String accessToken, String newRefreshTokenValue) {}
}
