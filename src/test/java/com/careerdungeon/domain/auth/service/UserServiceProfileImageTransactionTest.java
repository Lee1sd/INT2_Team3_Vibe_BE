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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CodeRabbit 리뷰(PR #125) — {@code updateProfileImage()}/{@code removeProfileImage()}가
 * 이전 S3 객체 삭제를 DB 커밋 전에 동기 호출하면, 커밋이 실패해 롤백될 때 DB는 여전히
 * 이전 키를 가리키는데 S3에는 그 객체가 이미 없어 이미지가 깨지는 상태가 남는다.
 * {@code UserService.deleteAfterCommit()}이 실제로 트랜잭션 커밋 이후로 삭제를 미루는지,
 * 롤백되면 아예 호출되지 않는지를 {@code UserServiceWithdrawTransactionTest}와 같은 방식
 * (Mockito 단위 테스트가 아니라 실제 트랜잭션 전파를 태우는 통합 테스트)으로 확인한다.
 */
@DataJpaTest
@Import(UserService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserServiceProfileImageTransactionTest {

    @Autowired
    UserService userService;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    ProfileImageStorageService profileImageStorageService;

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate transactionTemplate;
    Long userId;

    // 이 클래스는 Propagation.NOT_SUPPORTED라 테스트별 자동 롤백이 없고, 롤백을 검증하는
    // 테스트는 의도적으로 유저 row 자체는 커밋된 채로 남긴다(롤백 대상은 그 안의 변경뿐).
    // 테스트 실행 순서가 보장되지 않으므로, 모든 테스트가 같은 googleId를 재사용하면
    // 나중에 도는 테스트의 @BeforeEach가 UNIQUE 제약 위반으로 깨진다 — 테스트 메서드별로
    // 유니크한 googleId를 써서 서로 절대 충돌하지 않게 한다.
    @BeforeEach
    void setUp(TestInfo testInfo) {
        transactionTemplate = new TransactionTemplate(transactionManager);
        String googleId = "google-photo-tx-" + testInfo.getTestMethod().orElseThrow().getName();
        userId = transactionTemplate.execute(status ->
                userRepository.save(new User(googleId, "photo-tx@example.com", "홍길동")).getId());
        transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            user.updateProfileImageKey("old-key");
            return null;
        });
    }

    @Test
    @DisplayName("updateProfileImage()가 정상 커밋되면 커밋 이후 이전 S3 객체를 지운다")
    void updateProfileImage_onSuccessfulCommit_deletesPreviousKey() {
        MultipartFile file = new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        given(profileImageStorageService.upload(eq(userId), any())).willReturn("new-key");

        userService.updateProfileImage(userId, file);

        verify(profileImageStorageService).delete("old-key");
    }

    @Test
    @DisplayName("updateProfileImage()를 감싼 트랜잭션이 롤백되면 이전 S3 객체를 지우지 않는다")
    void updateProfileImage_whenOuterTransactionRollsBack_neverDeletesPreviousKey() {
        MultipartFile file = new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        given(profileImageStorageService.upload(eq(userId), any())).willReturn("new-key");

        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            userService.updateProfileImage(userId, file);
            throw new RuntimeException("강제 롤백");
        })).isInstanceOf(RuntimeException.class);

        verify(profileImageStorageService, never()).delete(any());
    }

    @Test
    @DisplayName("removeProfileImage()가 정상 커밋되면 커밋 이후 S3 객체를 지운다")
    void removeProfileImage_onSuccessfulCommit_deletesKey() {
        userService.removeProfileImage(userId);

        verify(profileImageStorageService).delete("old-key");
    }

    @Test
    @DisplayName("removeProfileImage()를 감싼 트랜잭션이 롤백되면 S3 객체를 지우지 않는다")
    void removeProfileImage_whenOuterTransactionRollsBack_neverDeletesKey() {
        assertThatThrownBy(() -> transactionTemplate.execute(status -> {
            userService.removeProfileImage(userId);
            throw new RuntimeException("강제 롤백");
        })).isInstanceOf(RuntimeException.class);

        verify(profileImageStorageService, never()).delete(any());
    }
}
