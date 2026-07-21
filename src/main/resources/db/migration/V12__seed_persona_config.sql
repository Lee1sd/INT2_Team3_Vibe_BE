-- Interviewer list requires canonical default persona master data in every environment.
INSERT INTO `persona_config` (`level`, `tone`)
SELECT 1, 'LENIENT'
WHERE NOT EXISTS (SELECT 1 FROM `persona_config` WHERE `level` = 1);

INSERT INTO `persona_config` (`level`, `tone`)
SELECT 2, 'STRICT'
WHERE NOT EXISTS (SELECT 1 FROM `persona_config` WHERE `level` = 2);

UPDATE `persona_config`
SET `tone` = 'LENIENT'
WHERE `level` = 1;

UPDATE `persona_config`
SET `tone` = 'STRICT'
WHERE `level` = 2;
