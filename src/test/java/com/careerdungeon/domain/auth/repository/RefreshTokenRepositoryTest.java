package com.careerdungeon.domain.auth.repository;

import com.careerdungeon.domain.auth.entity.RefreshToken;
import com.careerdungeon.domain.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    UserRepository userRepository;

    User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = userRepository.save(new User("google-123", "test@example.com", "테스터"));
    }

    @Test
    @DisplayName("저장된 tokenHash로 조회하면 RefreshToken이 반환된다")
    void findByTokenHash_존재하는_해시_토큰_반환() {
        refreshTokenRepository.save(
                RefreshToken.issue(savedUser, "hash-abc", LocalDateTime.now().plusDays(7)));

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("hash-abc");

        assertThat(found).isPresent();
        assertThat(found.get().isRevoked()).isFalse();
        assertThat(found.get().getUser().getGoogleId()).isEqualTo("google-123");
    }

    @Test
    @DisplayName("존재하지 않는 tokenHash로 조회하면 Optional.empty()가 반환된다")
    void findByTokenHash_없는_해시_빈값_반환() {
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("nonexistent-hash");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("deleteByUser 호출 시 해당 유저의 모든 RefreshToken이 삭제된다")
    void deleteByUser_해당_유저_토큰_전체_삭제() {
        refreshTokenRepository.save(
                RefreshToken.issue(savedUser, "hash-1", LocalDateTime.now().plusDays(7)));
        refreshTokenRepository.save(
                RefreshToken.issue(savedUser, "hash-2", LocalDateTime.now().plusDays(7)));

        refreshTokenRepository.deleteByUser(savedUser);

        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }
}
