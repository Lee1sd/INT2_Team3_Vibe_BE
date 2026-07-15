package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.repository.BadgeRepository;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** 가입 완료 소비 계약이 Lv.1 해금과 Stage1 뱃지를 함께 보장하는지 검증한다. */
@DataJpaTest
@Import({SignupProgressService.class, BadgeAwardService.class})
class SignupProgressServiceTest {

    @Autowired
    SignupProgressService signupProgressService;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    BadgeRepository badgeRepository;

    @Autowired
    UserBadgeRepository userBadgeRepository;

    @Autowired
    UserRepository userRepository;

    User user;

    /** 가입 초기화에 필요한 사용자와 Stage1 기준 데이터를 준비한다. */
    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("signup-progress", "signup@example.com", "가입 사용자"));
        badgeRepository.saveAndFlush(Badge.create(1, "Stage1 테스트 뱃지", "/badges/test-stage1.png"));
    }

    /** 가입 직후 상태가 Lv.1 해금·게이지 0·Stage1 지급인지 검증한다. */
    @Test
    @DisplayName("가입 직후 Lv.1이 열리고 게이지 0%와 Stage1 뱃지를 가진다")
    void 가입_직후_초기_진행도와_stage1_뱃지를_생성한다() {
        signupProgressService.initializeFor(user.getId());

        UserUnlockStatus status = userUnlockStatusRepository.findById(user.getId()).orElseThrow();
        assertThat(status.getUnlockedLevel()).isEqualTo(1);
        assertThat(status.getProgressGauge()).isZero();
        assertThat(userBadgeRepository.countByUserId(user.getId())).isEqualTo(1);
    }

    /** 가입 완료 신호를 중복 처리해도 상태와 뱃지가 늘어나지 않는지 검증한다. */
    @Test
    @DisplayName("가입 완료 신호를 중복 처리해도 진행도와 Stage1 뱃지는 한 건이다")
    void 중복_가입_초기화는_멱등하다() {
        signupProgressService.initializeFor(user.getId());
        signupProgressService.initializeFor(user.getId());

        assertThat(userUnlockStatusRepository.count()).isEqualTo(1);
        assertThat(userBadgeRepository.countByUserId(user.getId())).isEqualTo(1);
    }
}
