ALTER TABLE `messages`
    ADD CONSTRAINT `UQ_MESSAGES_SESSION_ROLE_TURN` UNIQUE (`session_id`, `role`, `turn`);
