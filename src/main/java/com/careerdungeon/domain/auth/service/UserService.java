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
     * 프로필 이미지 업로드/교체(ADR-018). 새 객체를 먼저 S3에 올리고 DB(user)를 갱신한
     * 뒤에야 이전 객체를 지운다 — 업로드 도중 실패해도 기존 사진이 깨지지 않는다.
     */
    @Transactional
    public ProfileImageResponse updateProfileImage(Long userId, MultipartFile file) {
        User user = findUser(userId);
        String previousKey = user.getProfileImageKey();

        String newKey = profileImageStorageService.upload(userId, file);
        user.updateProfileImageKey(newKey);

        if (previousKey != null) {
            profileImageStorageService.delete(previousKey);
        }
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
        profileImageStorageService.delete(previousKey);
    }

    /**
     * 회원 탈퇴: users 행을 그대로 삭제한다. 대화 기록/이력서/뱃지/진행도 등 관련 레코드는
     * 애플리케이션 코드가 아니라 DB의 ON DELETE CASCADE(V11__cascade_delete_on_user_withdrawal.sql,
     * ADR-016)가 자동으로 함께 지운다 — "전체 즉시 삭제" 정책(privacy-policy.md)에 따른 것이며,
     * auth 도메인이 다른 도메인 Repository를 직접 알 필요가 없게 하기 위한 설계다.
     * 프로필 이미지(S3, DB 밖 데이터)는 CASCADE 대상이 아니므로 별도로 삭제를 시도한다
     * (ADR-018) — 실패해도 탈퇴 자체는 진행한다(ProfileImageStorageService.delete는
     * best-effort).
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = findUser(userId);
        String profileImageKey = user.getProfileImageKey();
        if (profileImageKey != null) {
            profileImageStorageService.delete(profileImageKey);
        }
        userRepository.delete(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }
}
