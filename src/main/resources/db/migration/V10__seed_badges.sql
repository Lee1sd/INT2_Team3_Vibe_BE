-- 제공된 Level1~4 정적 자산을 기준으로 뱃지 표시 계약을 초기화한다.
-- 기존 환경에 같은 Stage가 있으면 아래 UPDATE에서 확정 이름·URL·조건으로 동기화한다.
INSERT INTO `badges` (`stage`, `name`, `image_url`, `unlock_condition`)
SELECT 1, '프로그래머쓱 LEVEL 1', '/badges/Level1.png', 'SIGNUP'
WHERE NOT EXISTS (SELECT 1 FROM `badges` WHERE `stage` = 1);

INSERT INTO `badges` (`stage`, `name`, `image_url`, `unlock_condition`)
SELECT 2, '프로그래머쓱 LEVEL 2', '/badges/Level2.png', 'LEVEL1_UNLOCK'
WHERE NOT EXISTS (SELECT 1 FROM `badges` WHERE `stage` = 2);

INSERT INTO `badges` (`stage`, `name`, `image_url`, `unlock_condition`)
SELECT 3, '프로그래머쓱 LEVEL 3', '/badges/Level3.png', 'LEVEL2_UNLOCK'
WHERE NOT EXISTS (SELECT 1 FROM `badges` WHERE `stage` = 3);

INSERT INTO `badges` (`stage`, `name`, `image_url`, `unlock_condition`)
SELECT 4, '프로그래머쓱 LEVEL 4', '/badges/Level4.png', 'LEVEL3_UNLOCK'
WHERE NOT EXISTS (SELECT 1 FROM `badges` WHERE `stage` = 4);

UPDATE `badges`
SET `name` = '프로그래머쓱 LEVEL 1',
    `image_url` = '/badges/Level1.png',
    `unlock_condition` = 'SIGNUP'
WHERE `stage` = 1;

UPDATE `badges`
SET `name` = '프로그래머쓱 LEVEL 2',
    `image_url` = '/badges/Level2.png',
    `unlock_condition` = 'LEVEL1_UNLOCK'
WHERE `stage` = 2;

UPDATE `badges`
SET `name` = '프로그래머쓱 LEVEL 3',
    `image_url` = '/badges/Level3.png',
    `unlock_condition` = 'LEVEL2_UNLOCK'
WHERE `stage` = 3;

UPDATE `badges`
SET `name` = '프로그래머쓱 LEVEL 4',
    `image_url` = '/badges/Level4.png',
    `unlock_condition` = 'LEVEL3_UNLOCK'
WHERE `stage` = 4;
