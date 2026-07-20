package com.careerdungeon.domain.progress.repository;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.entity.UserBadge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 뱃지 Stage와 사용자별 획득 중복을 DB 제약이 차단하는지 검증한다. */
@DataJpaTest
class BadgePersistenceConstraintTest {

    @Autowired
    BadgeRepository badgeRepository;

    @Autowired
    UserBadgeRepository userBadgeRepository;

    @Autowired
    UserRepository userRepository;

    /** 동일 Stage의 뱃지 기준 데이터를 두 건 저장할 수 없는지 검증한다. */
    @Test
    @DisplayName("뱃지 Stage는 DB에서 유일해야 한다")
    void 동일_stage_뱃지를_거부한다() {
        assertThatThrownBy(() -> badgeRepository.saveAndFlush(
                Badge.create(1, "중복 뱃지", "/badges/duplicate.png")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 한 사용자가 같은 뱃지를 두 번 획득할 수 없는지 검증한다. */
    @Test
    @DisplayName("사용자와 뱃지 조합은 DB에서 유일해야 한다")
    void 동일_사용자_뱃지_획득을_거부한다() {
        User user = userRepository.saveAndFlush(new User(
                "badge-constraint-user",
                "badge-constraint@example.com",
                "제약 테스트 사용자"));
        Badge badge = badgeRepository.findByStage(1).orElseThrow();
        userBadgeRepository.saveAndFlush(UserBadge.award(user.getId(), badge));

        assertThatThrownBy(() -> userBadgeRepository.saveAndFlush(UserBadge.award(user.getId(), badge)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
