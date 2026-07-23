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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** 뱃지 조회 서비스의 사용자 격리, 도감 정렬 및 연관 데이터 선조회 계약을 검증한다. */
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

    @MockitoBean
    BadgeImageUrlService badgeImageUrlService;

    /** 전체 도감을 반환하면서 인증 사용자의 획득 상태만 표시하는지 검증한다. */
    @Test
    @DisplayName("Stage1~4 도감을 오름차순으로 조회하고 사용자별 획득 상태를 표시한다")
    void getMyBadgesReturnsCatalogWithUserAcquisitionState() {
        User owner = createUser("owner");
        User other = createUser("other");
        Badge stageThree = badgeRepository.findByStage(3).orElseThrow();
        Badge stageOne = badgeRepository.findByStage(1).orElseThrow();
        userBadgeRepository.save(UserBadge.award(owner.getId(), stageThree));
        userBadgeRepository.save(UserBadge.award(owner.getId(), stageOne));
        userBadgeRepository.saveAndFlush(UserBadge.award(other.getId(), stageOne));
        given(badgeImageUrlService.createImageUrl(1, "badges/Level1.png"))
                .willReturn("https://s3.example/badges/Level1.png?signature=one");
        given(badgeImageUrlService.createImageUrl(3, "badges/Level3.png"))
                .willReturn("https://s3.example/badges/Level3.png?signature=three");

        UserBadgeListResponse response = badgeQueryService.getMyBadges(owner.getId());

        assertThat(response.badges()).extracting("stage").containsExactly(1, 3);
        assertThat(response.badges()).extracting("acquired").containsOnly(true);
        assertThat(response.catalog()).extracting("stage").containsExactly(1, 2, 3, 4);
        assertThat(response.catalog()).extracting("acquired")
                .containsExactly(true, false, true, false);
        assertThat(response.catalog().get(0).imageUrl())
                .isEqualTo("https://s3.example/badges/Level1.png?signature=one");
        assertThat(response.catalog().get(2).imageUrl())
                .isEqualTo("https://s3.example/badges/Level3.png?signature=three");
        assertThat(response.catalog().get(1).acquiredAt()).isNull();
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

    /** 획득 기록이 없는 사용자도 잠금 상태의 전체 이미지 도감을 받는지 검증한다. */
    @Test
    @DisplayName("획득 뱃지가 없는 사용자는 Stage1~4를 모두 잠금 상태로 반환한다")
    void getMyBadgesReturnsLockedCatalogWhenUserOwnsNothing() {
        User user = createUser("empty");

        UserBadgeListResponse response = badgeQueryService.getMyBadges(user.getId());

        assertThat(response.badges()).isEmpty();
        assertThat(response.catalog())
                .hasSize(4)
                .allMatch(badge -> !badge.acquired() && badge.acquiredAt() == null);
    }

    /** 사용자 격리 테스트에 사용할 실제 사용자를 생성한다. */
    private User createUser(String suffix) {
        return userRepository.save(new User(
                "badge-query-" + suffix,
                "badge-query-" + suffix + "@example.com",
                "뱃지 조회 사용자"));
    }
}
