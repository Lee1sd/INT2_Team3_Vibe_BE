package com.careerdungeon.domain.progress.repository;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 사용자 진행도 테이블의 사용자별 단일 행과 게이지 범위 제약을 검증한다. */
@DataJpaTest
class UserUnlockStatusRepositoryTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    /** user_id 기본 키가 사용자별 진행도 중복 생성을 차단하는지 검증한다. */
    @Test
    @DisplayName("동일 사용자 진행도는 두 개 생성할 수 없다")
    void 사용자별_진행도_중복을_차단한다() {
        long userId = createUser("duplicate");
        insertStatus(userId, 1, 0);

        assertThatThrownBy(() -> insertStatus(userId, 1, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** DB에서도 신뢰도 게이지가 100%를 초과해 저장되지 않는지 검증한다. */
    @Test
    @DisplayName("신뢰도 게이지는 DB에서 0~100 범위를 벗어날 수 없다")
    void 게이지_DB_범위_제약을_검증한다() {
        long userId = createUser("range");

        assertThatThrownBy(() -> insertStatus(userId, 4, 101))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** 존재하지 않는 사용자에게 진행도 행을 연결하지 못하도록 FK가 동작하는지 검증한다. */
    @Test
    @DisplayName("사용자 없이 진행도 상태만 생성할 수 없다")
    void 사용자_FK_제약을_검증한다() {
        assertThatThrownBy(() -> insertStatus(999L, 1, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** FK 검증에 사용할 실제 사용자를 생성하고 식별자를 반환한다. */
    private long createUser(String suffix) {
        return userRepository.saveAndFlush(new User(
                "progress-" + suffix,
                suffix + "@example.com",
                "진행도 사용자")).getId();
    }

    /** 엔티티 우회 입력으로 DB 제약을 직접 검증하기 위한 진행도 행을 삽입한다. */
    private void insertStatus(long userId, int unlockedLevel, int progressGauge) {
        jdbcTemplate.update(
                "insert into user_unlock_status (user_id, unlocked_level, progress_gauge) values (?, ?, ?)",
                userId,
                unlockedLevel,
                progressGauge);
    }
}
