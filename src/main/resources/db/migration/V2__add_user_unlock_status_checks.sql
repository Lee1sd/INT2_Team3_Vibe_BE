-- 사용자 진행도는 순차 Stage와 100% 상한을 벗어난 값을 DB에서도 저장하지 않는다.
ALTER TABLE `user_unlock_status`
    ADD CONSTRAINT `CHK_user_unlock_status_unlocked_level`
        CHECK (`unlocked_level` BETWEEN 1 AND 4);

ALTER TABLE `user_unlock_status`
    ADD CONSTRAINT `CHK_user_unlock_status_progress_gauge`
        CHECK (`progress_gauge` BETWEEN 0 AND 100);
