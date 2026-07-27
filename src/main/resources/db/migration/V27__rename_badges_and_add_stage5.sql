-- 뱃지 표시명을 면접관 직급에 맞추고 Stage5(임원머쓱)를 도감에 추가한다.
-- Stage1만 가입 기본(인턴머쓱), Stage2~5는 널널한 대리/깐깐한 과장/압박 부장/이중인격 임원.
-- MVP 지급 상한(Stage1~3)은 StageProgressionService에서 그대로 유지한다.

-- MySQL 8 / H2(MODE=MySQL) 모두 DROP CONSTRAINT 사용 (DROP CHECK는 H2에서 문법 오류)
ALTER TABLE `badges`
    DROP CONSTRAINT `CHK_badges_stage`;

ALTER TABLE `badges`
    ADD CONSTRAINT `CHK_badges_stage`
        CHECK (`stage` BETWEEN 1 AND 5);

UPDATE `badges`
SET `name` = '인턴머쓱'
WHERE `stage` = 1;

UPDATE `badges`
SET `name` = '대리머쓱'
WHERE `stage` = 2;

UPDATE `badges`
SET `name` = '과장머쓱'
WHERE `stage` = 3;

UPDATE `badges`
SET `name` = '부장머쓱'
WHERE `stage` = 4;

INSERT INTO `badges` (`stage`, `name`, `image_url`, `unlock_condition`)
SELECT 5, '임원머쓱', 'badges/Level5.png', 'LEVEL4_UNLOCK'
WHERE NOT EXISTS (SELECT 1 FROM `badges` WHERE `stage` = 5);

UPDATE `badges`
SET `name` = '임원머쓱',
    `image_url` = 'badges/Level5.png',
    `unlock_condition` = 'LEVEL4_UNLOCK'
WHERE `stage` = 5;
