package com.careerdungeon.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findBySession_IdAndRoleAndTurn(Long sessionId, MessageRole role, int turn);

    boolean existsBySession_IdAndRoleAndTurn(Long sessionId, MessageRole role, int turn);

    List<Message> findAllBySession_IdAndRoleInOrderByTurnAscRoleAsc(Long sessionId, List<MessageRole> roles);
}
