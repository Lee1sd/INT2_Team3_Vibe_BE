package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Autowired
    PlatformTransactionManager transactionManager;

    TransactionTemplate transactionTemplate;
    Long userId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        userId = transactionTemplate.execute(status ->
                userRepository.save(new User("google-withdraw-tx", "tx@example.com", "홍길동")).getId());
    }

    @Test
    @DisplayName("withdraw()는 자체 쓰기 트랜잭션으로 실행되어 실제로 커밋(삭제)된다")
    void withdraw_actuallyCommitsDeleteDespiteReadOnlyClassDefault() {
        userService.withdraw(userId);

        Optional<User> reloaded = transactionTemplate.execute(status -> userRepository.findById(userId));
        assertThat(reloaded).isEmpty();
    }
}
