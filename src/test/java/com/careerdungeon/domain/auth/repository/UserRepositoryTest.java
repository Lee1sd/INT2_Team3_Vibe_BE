package com.careerdungeon.domain.auth.repository;

import com.careerdungeon.domain.auth.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Test
    @DisplayName("저장된 googleId로 조회하면 User가 반환된다")
    void findByGoogleId_존재하는_googleId_유저_반환() {
        userRepository.save(new User("google-123", "test@example.com", "테스터"));

        Optional<User> found = userRepository.findByGoogleId("google-123");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getName()).isEqualTo("테스터");
    }

    @Test
    @DisplayName("존재하지 않는 googleId로 조회하면 Optional.empty()가 반환된다")
    void findByGoogleId_없는_googleId_빈값_반환() {
        Optional<User> found = userRepository.findByGoogleId("nonexistent-id");

        assertThat(found).isEmpty();
    }
}
