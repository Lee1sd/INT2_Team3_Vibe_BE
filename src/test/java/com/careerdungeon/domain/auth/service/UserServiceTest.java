package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.dto.UserUpdateResponse;
import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void updateName_withExistingUser_updatesAndReturnsResponse() {
        User user = new User("google-123", "test@example.com", "기존이름");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserUpdateResponse response = userService.updateName(1L, "새이름");

        assertThat(response.name()).isEqualTo("새이름");
        assertThat(user.getName()).isEqualTo("새이름");
    }

    @Test
    void updateName_withNonExistentUser_throwsUserNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateName(99L, "이름"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    void withdraw_withExistingUser_deletesUserRow() {
        User user = new User("google-123", "test@example.com", "홍길동");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.withdraw(1L);

        verify(userRepository).delete(user);
    }

    @Test
    void withdraw_withNonExistentUser_throwsUserNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }
}
