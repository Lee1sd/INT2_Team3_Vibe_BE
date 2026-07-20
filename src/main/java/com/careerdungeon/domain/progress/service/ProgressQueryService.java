package com.careerdungeon.domain.progress.service;

import com.careerdungeon.domain.progress.dto.UserProgressResponse;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.exception.UserProgressNotFoundException;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 진행도 상태를 읽기 전용 API 응답으로 변환한다. */
@Service
@Transactional(readOnly = true)
public class ProgressQueryService {

    private final UserUnlockStatusRepository userUnlockStatusRepository;

    /** 진행도 저장소를 주입해 조회 서비스를 구성한다. */
    public ProgressQueryService(UserUnlockStatusRepository userUnlockStatusRepository) {
        this.userUnlockStatusRepository = userUnlockStatusRepository;
    }

    /** 인증 사용자의 현재 해금 레벨과 누적 게이지를 조회한다. */
    public UserProgressResponse getMyProgress(long userId) {
        UserUnlockStatus status = userUnlockStatusRepository.findById(userId)
                .orElseThrow(() -> new UserProgressNotFoundException(userId));
        return UserProgressResponse.from(status);
    }
}
