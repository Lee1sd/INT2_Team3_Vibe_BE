package com.careerdungeon.domain.auth.repository;

import com.careerdungeon.domain.auth.entity.RefreshToken;
import com.careerdungeon.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);
}
