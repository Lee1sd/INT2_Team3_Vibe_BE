package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.entity.RefreshToken;
import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.RefreshTokenRepository;
import com.careerdungeon.global.exception.BusinessException;
import com.careerdungeon.global.security.JwtProvider;
import com.careerdungeon.global.util.TokenHashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    private final User testUser = new User("google-123", "test@example.com", "테스트");

    @Test
    void login_deletesOldTokens_andReturnsNewPair() {
        when(jwtProvider.generateAccessToken(any())).thenReturn("access-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthService.LoginResult result = authService.login(testUser);

        verify(refreshTokenRepository).deleteByUser(testUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshTokenValue()).isNotBlank();
    }

    @Test
    void refresh_withValidToken_rotatesAndReturnsNewPair() {
        String plainToken = "valid-refresh-token-value";
        String tokenHash = TokenHashUtil.hash(plainToken);
        RefreshToken stored = RefreshToken.issue(testUser, tokenHash, LocalDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
        when(jwtProvider.generateAccessToken(any())).thenReturn("new-access-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthService.RefreshResult result = authService.refresh(plainToken);

        assertThat(stored.isRevoked()).isTrue();
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.newRefreshTokenValue()).isNotBlank().isNotEqualTo(plainToken);
    }

    @Test
    void refresh_withUnknownTokenHash_throwsInvalidException() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않은");
    }

    @Test
    void refresh_withRevokedToken_throwsExpiredException() {
        String plainToken = "revoked-token";
        String tokenHash = TokenHashUtil.hash(plainToken);
        RefreshToken stored = RefreshToken.issue(testUser, tokenHash, LocalDateTime.now().plusDays(7));
        stored.revoke();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(plainToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("만료되었거나 취소된");
    }

    @Test
    void refresh_withExpiredToken_throwsExpiredException() {
        String plainToken = "expired-token";
        String tokenHash = TokenHashUtil.hash(plainToken);
        RefreshToken stored = RefreshToken.issue(testUser, tokenHash, LocalDateTime.now().minusSeconds(1));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(plainToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("만료되었거나 취소된");
    }

    @Test
    void logout_withValidToken_revokesStoredToken() {
        String plainToken = "valid-logout-token";
        String tokenHash = TokenHashUtil.hash(plainToken);
        RefreshToken stored = RefreshToken.issue(testUser, tokenHash, LocalDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));

        authService.logout(plainToken);

        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    void logout_withNullToken_doesNothing() {
        assertThatCode(() -> authService.logout(null)).doesNotThrowAnyException();
        verifyNoInteractions(refreshTokenRepository);
    }
}
