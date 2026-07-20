package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.dto.UserProgressResponse;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.exception.UserProgressNotFoundException;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 진행도 조회 서비스가 저장 상태와 미존재 분기를 올바르게 변환하는지 검증한다. */
@DataJpaTest
@Import(ProgressQueryService.class)
class ProgressQueryServiceTest {

    @Autowired
    ProgressQueryService progressQueryService;

    @Autowired
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Autowired
    UserRepository userRepository;

    /** 가입 초기 상태를 UM-001 응답으로 변환하는지 검증한다. */
    @Test
    @DisplayName("저장된 사용자 진행도를 조회한다")
    void getMyProgressReturnsStoredState() {
        User user = userRepository.save(new User("progress-query", "progress-query@example.com", "조회 사용자"));
        userUnlockStatusRepository.saveAndFlush(UserUnlockStatus.initialFor(user));

        UserProgressResponse response = progressQueryService.getMyProgress(user.getId());

        assertThat(response.unlockedLevel()).isEqualTo(1);
        assertThat(response.progressGauge()).isZero();
    }

    /** 가입 초기화가 누락된 사용자를 명시적인 도메인 예외로 처리하는지 검증한다. */
    @Test
    @DisplayName("진행도 상태가 없으면 UserProgressNotFoundException을 던진다")
    void getMyProgressRejectsMissingState() {
        assertThatThrownBy(() -> progressQueryService.getMyProgress(999L))
                .isInstanceOf(UserProgressNotFoundException.class);
    }
}
