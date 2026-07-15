package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.exception.SignupUserNotFoundException;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가입 완료 사용자의 초기 해금 상태와 Stage1 뱃지를 함께 생성한다. */
@Service
public class SignupProgressService {

    private static final int SIGNUP_BADGE_STAGE = 1;

    private final EntityManager entityManager;
    private final UserUnlockStatusRepository userUnlockStatusRepository;
    private final BadgeAwardService badgeAwardService;

    /** 가입 사용자·초기 진행도 저장소와 뱃지 지급 서비스를 주입받는다. */
    public SignupProgressService(
            EntityManager entityManager,
            UserUnlockStatusRepository userUnlockStatusRepository,
            BadgeAwardService badgeAwardService) {
        this.entityManager = entityManager;
        this.userUnlockStatusRepository = userUnlockStatusRepository;
        this.badgeAwardService = badgeAwardService;
    }

    /**
     * 가입 사용자의 Lv.1 해금·게이지 0% 상태와 Stage1 뱃지를 원자적으로 보장한다.
     * 동일 가입 완료 신호가 다시 전달돼도 기존 상태와 뱃지를 재사용한다.
     */
    @Transactional
    public void initializeFor(long userId) {
        // 동일 가입 신호를 직렬화해 초기 진행도와 Stage1 뱃지의 동시 중복 생성을 막는다.
        User user = entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
        if (user == null) {
            throw new SignupUserNotFoundException(userId);
        }

        userUnlockStatusRepository.findById(userId)
                .orElseGet(() -> userUnlockStatusRepository.save(UserUnlockStatus.initialFor(user)));
        badgeAwardService.awardForStage(userId, SIGNUP_BADGE_STAGE);
    }
}
