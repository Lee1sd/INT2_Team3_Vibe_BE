package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.dto.UserBadgeListResponse;
import com.careerdungeon.domain.progress.entity.Badge;
import com.careerdungeon.domain.progress.entity.UserBadge;
import com.careerdungeon.domain.progress.repository.BadgeRepository;
import com.careerdungeon.domain.progress.repository.UserBadgeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** 뱃지 조회 서비스의 사용자 격리, 정렬 및 연관 데이터 선조회 계약을 검증한다. */
@DataJpaTest
@Import(BadgeQueryService.class)
class BadgeQueryServiceTest {

    @Autowired
    BadgeQueryService badgeQueryService;

    @Autowired
    BadgeRepository badgeRepository;

    @Autowired
    UserBadgeRepository userBadgeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EntityManager entityManager;

    /** 인증 사용자 뱃지만 Stage 오름차순으로 반환하는지 검증한다. */
    @Test
    @DisplayName("사용자별 획득 뱃지를 Stage 오름차순으로 조회한다")
    void getMyBadgesReturnsOnlyOwnedBadgesInStageOrder() {
        User owner = createUser("owner");
        User other = createUser("other");
        Badge stageThree = badgeRepository.findByStage(3).orElseThrow();
        Badge stageOne = badgeRepository.findByStage(1).orElseThrow();
        userBadgeRepository.save(UserBadge.award(owner.getId(), stageThree));
        userBadgeRepository.save(UserBadge.award(owner.getId(), stageOne));
        userBadgeRepository.saveAndFlush(UserBadge.award(other.getId(), stageOne));

        UserBadgeListResponse response = badgeQueryService.getMyBadges(owner.getId());

        assertThat(response.badges()).extracting("stage").containsExactly(1, 3);
        assertThat(response.badges()).extracting("name")
                .containsExactly("프로그래머쓱 LEVEL 1", "프로그래머쓱 LEVEL 3");
    }

    /** fetch join 조회가 DTO 변환 전 Badge 연관을 초기화해 N+1을 방지하는지 검증한다. */
    @Test
    @DisplayName("뱃지 표시 정보는 사용자 획득 기록과 함께 한 번에 조회한다")
    void repositoryFetchesBadgeAssociationEagerlyForQuery() {
        User user = createUser("fetch");
        Badge badge = badgeRepository.findByStage(2).orElseThrow();
        userBadgeRepository.saveAndFlush(UserBadge.award(user.getId(), badge));
        entityManager.clear();

        UserBadge loaded = userBadgeRepository
                .findAllWithBadgeByUserIdOrderByStage(user.getId())
                .get(0);
        PersistenceUnitUtil persistenceUnitUtil = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil();

        assertThat(persistenceUnitUtil.isLoaded(loaded, "badge")).isTrue();
    }

    /** 획득 기록이 없는 사용자의 정상 빈 목록 계약을 검증한다. */
    @Test
    @DisplayName("획득 뱃지가 없는 사용자는 빈 목록을 반환한다")
    void getMyBadgesReturnsEmptyList() {
        User user = createUser("empty");

        assertThat(badgeQueryService.getMyBadges(user.getId()).badges()).isEmpty();
    }

    /** 사용자 격리 테스트에 사용할 실제 사용자를 생성한다. */
    private User createUser(String suffix) {
        return userRepository.save(new User(
                "badge-query-" + suffix,
                "badge-query-" + suffix + "@example.com",
                "뱃지 조회 사용자"));
    }
}
