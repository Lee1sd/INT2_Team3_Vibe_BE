-- PersonaConfigRepository.findByLevel assumes a single row per level.
ALTER TABLE `persona_config`
    ADD CONSTRAINT `UK_persona_config_level` UNIQUE (`level`);
