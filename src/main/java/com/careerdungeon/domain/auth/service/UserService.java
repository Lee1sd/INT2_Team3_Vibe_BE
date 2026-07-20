package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.dto.UserUpdateResponse;
import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserUpdateResponse updateName(Long userId, String name) {
        User user = findUser(userId);
        user.updateName(name);
        return new UserUpdateResponse(user.getId(), user.getName());
    }

    /**
     * 회원 탈퇴: users 행을 그대로 삭제한다. 대화 기록/이력서/뱃지/진행도 등 관련 레코드는
     * 애플리케이션 코드가 아니라 DB의 ON DELETE CASCADE(V11__cascade_delete_on_user_withdrawal.sql,
     * ADR-016)가 자동으로 함께 지운다 — "전체 즉시 삭제" 정책(privacy-policy.md)에 따른 것이며,
     * auth 도메인이 다른 도메인 Repository를 직접 알 필요가 없게 하기 위한 설계다.
     */
    public void withdraw(Long userId) {
        User user = findUser(userId);
        userRepository.delete(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }
}
