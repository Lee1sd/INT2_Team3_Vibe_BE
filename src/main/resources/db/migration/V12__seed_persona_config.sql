-- Interviewer list requires canonical default persona master data in every environment.
CREATE TEMPORARY TABLE `persona_config_seed_candidates` (
    `level` INT NOT NULL,
    `tone` VARCHAR(20) NOT NULL
);

INSERT INTO `persona_config_seed_candidates` (`level`, `tone`)
VALUES
    (1, 'LENIENT'),
    (2, 'STRICT');

DELETE FROM `persona_config_seed_candidates`
WHERE `level` IN (SELECT `level` FROM `persona_config`);

INSERT INTO `persona_config` (`level`, `tone`)
SELECT `level`, `tone`
FROM `persona_config_seed_candidates`;

DROP TABLE `persona_config_seed_candidates`;

UPDATE `persona_config`
SET `tone` = 'LENIENT'
WHERE `level` = 1;

UPDATE `persona_config`
SET `tone` = 'STRICT'
WHERE `level` = 2;
