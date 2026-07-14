package com.careerdungeon.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    // application-test.yml 의 jwt.secret 과 동일값
    private static final String TEST_SECRET =
            "dGVzdC1qd3Qtc2VjcmV0LWtleS1mb3ItdW5pdC10ZXN0cy1vbmx5LW5vdC1wcm9k";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(TEST_SECRET);
    }

    @Test
    void generateAccessToken_thenValidateReturnsTrue() {
        String token = jwtProvider.generateAccessToken(1L);
        assertThat(jwtProvider.validateToken(token)).isTrue();
    }

    @Test
    void generateAccessToken_thenGetUserIdReturnsCorrectId() {
        String token = jwtProvider.generateAccessToken(42L);
        assertThat(jwtProvider.getUserId(token)).isEqualTo(42L);
    }

    @Test
    void validateToken_withTamperedToken_returnsFalse() {
        String token = jwtProvider.generateAccessToken(1L);
        assertThat(jwtProvider.validateToken(token + "tampered")).isFalse();
    }

    @Test
    void validateToken_withEmptyString_returnsFalse() {
        assertThat(jwtProvider.validateToken("")).isFalse();
    }

    @Test
    void validateToken_withRandomString_returnsFalse() {
        assertThat(jwtProvider.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void generateTokens_forDifferentUsers_produceDifferentTokens() {
        String tokenA = jwtProvider.generateAccessToken(1L);
        String tokenB = jwtProvider.generateAccessToken(2L);
        assertThat(tokenA).isNotEqualTo(tokenB);
    }
}
