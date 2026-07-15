package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.exception.BadgeNotFoundException;
import com.careerdungeon.domain.progress.repository.BadgeRepository;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 JPA 저장소로 사용자 뱃지의 지급 및 중복 방지를 검증한다. */
@DataJpaTest
@Import(BadgeAwardService.class)
class BadgeAwardServiceTest {

    @Autowired
    BadgeAwardService badgeAwardService;

    @Autowired
    BadgeRepository badgeRepository;

    @Autowired
    UserBadgeRepository userBadgeRepository;

    @Autowired
    UserRepository userRepository;

    long userId;

    /** 각 테스트에서 사용할 저장된 사용자와 Stage1 뱃지를 준비한다. */
    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User("badge-user", "badge@example.com", "뱃지 사용자"));
        userId = user.getId();
        badgeRepository.saveAndFlush(Badge.create(1, "Stage1 테스트 뱃지", "/badges/test-stage1.png"));
    }

    /** 동일 Stage 지급을 반복해도 획득 레코드가 한 건인지 검증한다. */
    @Test
    @DisplayName("같은 뱃지를 반복 지급해도 사용자 획득 기록은 한 건이다")
    void 동일_뱃지_지급은_멱등하다() {
        boolean first = badgeAwardService.awardForStage(userId, 1);
        boolean duplicate = badgeAwardService.awardForStage(userId, 1);

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(userBadgeRepository.countByUserId(userId)).isEqualTo(1);
    }

    /** 지급할 기준 데이터가 없을 때 명시적인 도메인 예외가 발생하는지 검증한다. */
    @Test
    @DisplayName("지급할 Stage의 뱃지 기준 데이터가 없으면 실패한다")
    void 뱃지_기준_데이터_누락을_거부한다() {
        assertThatThrownBy(() -> badgeAwardService.awardForStage(userId, 2))
                .isInstanceOf(BadgeNotFoundException.class);

        assertThat(userBadgeRepository.countByUserId(userId)).isZero();
    }
}
