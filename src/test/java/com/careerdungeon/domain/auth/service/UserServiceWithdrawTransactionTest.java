package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PR #100 리뷰(이건희)에서 지적된 회귀를 재현/방지한다: {@code UserService}는 클래스 레벨
 * {@code @Transactional(readOnly = true)}를 쓰는데, {@code withdraw()}에 자체
 * {@code @Transactional}이 없으면 readOnly 트랜잭션을 그대로 물려받는다. Hibernate는
 * readOnly 트랜잭션에서 flush를 자동으로 하지 않을 수 있어, {@code userRepository.delete()}가
 * 예외 없이 "조용히" DB에 반영 안 될 수 있다 — Mockito 단위 테스트(delete() 호출 여부만
 * 검증)로는 이 클래스의 버그를 못 잡기 때문에, 실제 트랜잭션 전파를 태우는 통합 테스트로
 * 확인한다. 테스트 클래스 자체는 {@code Propagation.NOT_SUPPORTED}로 트랜잭션 밖에 두고,
 * {@code userService.withdraw()} 호출이 실제 프록시를 거쳐 자기 자신의 트랜잭션 경계로
 * 커밋되는지 검증한다.
 */
@DataJpaTest
@Import(UserService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserServiceWithdrawTransactionTest {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    // 프로필 이미지 S3 연동(ADR-020)은 이 테스트의 관심사가 아니다 — 이 테스트가 만드는
    // 유저는 profileImageKey가 없어 실제로 호출되지도 않지만, UserService 생성자가
    // 요구하므로 목으로 채워 컨텍스트가 뜨게 한다.
    @MockitoBean
    ProfileImageStorageService profileImageStorageService;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate transactionTemplate;
    Long userId;

    // 롤백을 검증하는 테스트는 유저 row 자체를 커밋된 채로 남긴다(NOT_SUPPORTED라
    // 테스트별 자동 롤백이 없음). 테스트 실행 순서가 보장되지 않으므로, 두 테스트가 같은
    // googleId를 쓰면 나중에 도는 테스트의 @BeforeEach가 UNIQUE 제약 위반으로 깨진다 —
    // 테스트 메서드별로 유니크한 googleId를 쓴다.
    @BeforeEach
    void setUp(TestInfo testInfo) {
        transactionTemplate = new TransactionTemplate(transactionManager);
        String googleId = "google-withdraw-tx-" + testInfo.getTestMethod().orElseThrow().getName();
        userId = transactionTemplate.execute(status ->
                userRepository.save(new User(googleId, "tx@example.com", "홍길동")).getId());
    }

    @Test
    @DisplayName("withdraw()는 자체 쓰기 트랜잭션으로 실행되어 실제로 커밋(삭제)된다")
    void withdraw_actuallyCommitsDeleteDespiteReadOnlyClassDefault() {
        userService.withdraw(userId);

        Optional<User> reloaded = transactionTemplate.execute(status -> userRepository.findById(userId));
        assertThat(reloaded).isEmpty();
    }

    // CodeRabbit 리뷰(PR #125) — withdraw()가 프로필 이미지 S3 삭제를 DB 삭제 커밋 전에
    // 동기 호출하면, CASCADE 삭제가 실패해 롤백될 때 DB에는 유저가 남아있는데 S3 이미지는
    // 이미 사라진 상태가 될 수 있다. withdraw()를 감싼 바깥 트랜잭션이 롤백되면 S3
    // 삭제(deleteAfterCommit)가 아예 호출되지 않아야 한다.
    @Test
    @DisplayName("withdraw()를 감싼 트랜잭션이 롤백되면 프로필 이미지 S3 객체를 지우지 않는다")
    void withdraw_whenOuterTransactionRollsBack_neverDeletesProfileImage() {
        transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            user.updateProfileImageKey("old-key");
            return null;
        });

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            userService.withdraw(userId);
            throw new RuntimeException("강제 롤백");
        })).isInstanceOf(RuntimeException.class);

        verify(profileImageStorageService, never()).delete(any());
    }
}
