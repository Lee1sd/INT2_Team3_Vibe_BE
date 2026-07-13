package com.careerdungeon.domain.persona;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonaConfigRepository extends JpaRepository<PersonaConfig, Long> {

    Optional<PersonaConfig> findByLevel(int level);
}
