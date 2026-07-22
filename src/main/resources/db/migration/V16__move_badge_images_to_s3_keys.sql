-- private S3 Presigned GET 계약에 맞춰 기존 정적 상대 경로를 고정 object key로 전환한다.
UPDATE `badges` SET `image_url` = 'badges/Level1.png' WHERE `stage` = 1;
UPDATE `badges` SET `image_url` = 'badges/Level2.png' WHERE `stage` = 2;
UPDATE `badges` SET `image_url` = 'badges/Level3.png' WHERE `stage` = 3;
UPDATE `badges` SET `image_url` = 'badges/Level4.png' WHERE `stage` = 4;
