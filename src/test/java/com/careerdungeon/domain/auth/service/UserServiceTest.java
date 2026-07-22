package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.dto.ProfileImageResponse;
import com.careerdungeon.domain.auth.dto.UserResponse;
import com.careerdungeon.domain.auth.dto.UserUpdateResponse;
import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileImageStorageService profileImageStorageService;

    @InjectMocks
    private UserService userService;

    @Test
    void getMe_withExistingUser_returnsIdNameEmail() {
        User user = new User("google-123", "test@example.com", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        given(profileImageStorageService.presignedUrl(isNull())).willReturn(null);

        UserResponse response = userService.getMe(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.photoUrl()).isNull();
    }

    @Test
    void getMe_withProfileImage_includesPresignedPhotoUrl() {
        User user = new User("google-123", "test@example.com", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.updateProfileImageKey("profile-images/1/abc.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        given(profileImageStorageService.presignedUrl("profile-images/1/abc.jpg"))
                .willReturn("https://bucket.s3.amazonaws.com/profile-images/1/abc.jpg?X-Amz-Signature=...");

        UserResponse response = userService.getMe(1L);

        assertThat(response.photoUrl()).isEqualTo("https://bucket.s3.amazonaws.com/profile-images/1/abc.jpg?X-Amz-Signature=...");
    }

    @Test
    void getMe_withNonExistentUser_throwsUserNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

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
    void updateProfileImage_withNoPreviousImage_uploadsAndDoesNotDelete() {
        User user = new User("google-123", "test@example.com", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        MultipartFile file = new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        given(profileImageStorageService.upload(eq(1L), eq(file))).willReturn("profile-images/1/new.jpg");
        given(profileImageStorageService.presignedUrl("profile-images/1/new.jpg"))
                .willReturn("https://example.com/new.jpg");

        ProfileImageResponse response = userService.updateProfileImage(1L, file);

        assertThat(response.photoUrl()).isEqualTo("https://example.com/new.jpg");
        assertThat(user.getProfileImageKey()).isEqualTo("profile-images/1/new.jpg");
        verify(profileImageStorageService, never()).delete(any());
    }

    @Test
    void updateProfileImage_withExistingImage_deletesPreviousKeyAfterSavingNewOne() {
        User user = new User("google-123", "test@example.com", "홍길동");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.updateProfileImageKey("profile-images/1/old.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        MultipartFile file = new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
        given(profileImageStorageService.upload(eq(1L), eq(file))).willReturn("profile-images/1/new.jpg");

        userService.updateProfileImage(1L, file);

        assertThat(user.getProfileImageKey()).isEqualTo("profile-images/1/new.jpg");
        verify(profileImageStorageService).delete("profile-images/1/old.jpg");
    }

    @Test
    void removeProfileImage_withExistingImage_clearsKeyAndDeletesObject() {
        User user = new User("google-123", "test@example.com", "홍길동");
        user.updateProfileImageKey("profile-images/1/old.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.removeProfileImage(1L);

        assertThat(user.getProfileImageKey()).isNull();
        verify(profileImageStorageService).delete("profile-images/1/old.jpg");
    }

    @Test
    void removeProfileImage_withNoImage_isIdempotentAndDoesNotCallDelete() {
        User user = new User("google-123", "test@example.com", "홍길동");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.removeProfileImage(1L);

        verify(profileImageStorageService, never()).delete(any());
    }

    @Test
    void withdraw_withProfileImage_deletesS3ObjectAndDeletesUserRow() {
        User user = new User("google-123", "test@example.com", "홍길동");
        user.updateProfileImageKey("profile-images/1/old.jpg");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.withdraw(1L);

        verify(profileImageStorageService).delete("profile-images/1/old.jpg");
        verify(userRepository).delete(user);
    }

    @Test
    void withdraw_withoutProfileImage_doesNotCallDelete() {
        User user = new User("google-123", "test@example.com", "홍길동");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.withdraw(1L);

        verify(profileImageStorageService, never()).delete(any());
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
