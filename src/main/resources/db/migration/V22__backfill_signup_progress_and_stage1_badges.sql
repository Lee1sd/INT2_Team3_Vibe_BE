-- 뱃지 기능 도입 전에 가입한 사용자에게 누락된 초기 진행도 상태를 보정한다.
INSERT INTO user_unlock_status (user_id, unlocked_level, progress_gauge)
SELECT users.id, 1, 0
FROM users
LEFT JOIN user_unlock_status ON user_unlock_status.user_id = users.id
WHERE user_unlock_status.user_id IS NULL;

-- 기존 사용자도 가입 시점 계약과 동일하게 Stage1 뱃지를 한 번만 보유하도록 보정한다.
INSERT INTO user_badges (user_id, badge_id, acquired_at)
SELECT users.id, badges.id, users.created_at
FROM users
JOIN badges ON badges.stage = 1
LEFT JOIN user_badges
    ON user_badges.user_id = users.id
    AND user_badges.badge_id = badges.id
WHERE user_badges.id IS NULL;
