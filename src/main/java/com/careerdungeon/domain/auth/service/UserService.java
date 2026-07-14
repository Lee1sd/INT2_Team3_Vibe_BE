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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
        user.updateName(name);
        return new UserUpdateResponse(user.getId(), user.getName());
    }
}
