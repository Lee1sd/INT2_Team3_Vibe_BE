package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.dto.ProfileImageResponse;
import com.careerdungeon.domain.auth.dto.UserResponse;
import com.careerdungeon.domain.auth.dto.UserUpdateResponse;
import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ProfileImageStorageService profileImageStorageService;

    public UserService(UserRepository userRepository, ProfileImageStorageService profileImageStorageService) {
        this.userRepository = userRepository;
        this.profileImageStorageService = profileImageStorageService;
    }

    public UserResponse getMe(Long userId) {
        User user = findUser(userId);
        return UserResponse.from(user, profileImageStorageService.presignedUrl(user.getProfileImageKey()));
    }

    @Transactional
    public UserUpdateResponse updateName(Long userId, String name) {
        User user = findUser(userId);
        user.updateName(name);
        return new UserUpdateResponse(user.getId(), user.getName());
    }

    /**
     * 프로필 이미지 업로드/교체(ADR-020). 새 객체를 먼저 S3에 올리고 DB(user)를 갱신한
     * 뒤에야 이전 객체를 지운다 — 업로드 도중 실패해도 기존 사진이 깨지지 않는다. 이전
     * 객체 삭제는 트랜잭션이 실제로 커밋된 뒤로 미룬다(아래 {@code deleteAfterCommit}) —
     * 커밋 전에 지우면, 커밋이 실패해 롤백될 때 DB는 여전히 이전 키를 가리키는데 S3에는
     * 그 객체가 이미 없어 이미지가 깨지는 상태가 남는다(CodeRabbit 리뷰, PR #125).
     */
    @Transactional
    public ProfileImageResponse updateProfileImage(Long userId, MultipartFile file) {
        User user = findUser(userId);
        String previousKey = user.getProfileImageKey();

        String newKey = profileImageStorageService.upload(userId, file);
        user.updateProfileImageKey(newKey);

        deleteAfterCommit(previousKey);
        return new ProfileImageResponse(profileImageStorageService.presignedUrl(newKey));
    }

    /** 프로필 이미지 제거(UP-005). 이미지가 없는 상태에서 호출해도 멱등하게 성공한다. */
    @Transactional
    public void removeProfileImage(Long userId) {
        User user = findUser(userId);
        String previousKey = user.getProfileImageKey();
        if (previousKey == null) {
            return;
        }
        user.updateProfileImageKey(null);
        deleteAfterCommit(previousKey);
    }

    /**
     * 회원 탈퇴: users 행을 그대로 삭제한다. 대화 기록/이력서/뱃지/진행도 등 관련 레코드는
     * 애플리케이션 코드가 아니라 DB의 ON DELETE CASCADE(V11__cascade_delete_on_user_withdrawal.sql,
     * ADR-016)가 자동으로 함께 지운다 — "전체 즉시 삭제" 정책(privacy-policy.md)에 따른 것이며,
     * auth 도메인이 다른 도메인 Repository를 직접 알 필요가 없게 하기 위한 설계다.
     * 프로필 이미지(S3, DB 밖 데이터)는 CASCADE 대상이 아니므로 별도로 삭제를 시도한다
     * (ADR-020) — 실패해도 탈퇴 자체는 진행한다(ProfileImageStorageService.delete는
     * best-effort). CASCADE가 FK 여러 개를 연쇄로 지우다 실패해 롤백될 가능성이 단순
     * UPDATE보다 높으므로, S3 삭제도 다른 메서드와 동일하게 커밋 이후로 미룬다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = findUser(userId);
        String profileImageKey = user.getProfileImageKey();
        userRepository.delete(user);
        deleteAfterCommit(profileImageKey);
    }

    /**
     * S3 객체 삭제를 현재 트랜잭션이 커밋된 뒤로 미룬다. 활성 트랜잭션이 없을 때(예:
     * 트랜잭션 없이 직접 호출되는 테스트)는 커밋을 기다릴 수 없으므로 즉시 삭제한다.
     */
    private void deleteAfterCommit(String key) {
        if (key == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    profileImageStorageService.delete(key);
                }
            });
        } else {
            profileImageStorageService.delete(key);
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }
}
