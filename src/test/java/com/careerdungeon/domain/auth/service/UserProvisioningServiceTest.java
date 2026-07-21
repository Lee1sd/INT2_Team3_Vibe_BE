package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.service.SignupProgressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SignupProgressService signupProgressService;

    @Test
    void provisionOAuthUser_createsSignupProgressWhenGoogleUserDoesNotExist() {
        UserProvisioningService service = new UserProvisioningService(userRepository, signupProgressService);

        given(userRepository.findByGoogleId("google-new")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 10L);
            return user;
        });

        User result = service.provisionOAuthUser("google-new", "new@example.com", "김한비");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getGoogleId()).isEqualTo("google-new");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(userCaptor.getValue().getName()).isEqualTo("김한비");
        verify(signupProgressService).initializeFor(10L);
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void provisionOAuthUser_doesNotCreateSignupProgressWhenGoogleUserAlreadyExists() {
        UserProvisioningService service = new UserProvisioningService(userRepository, signupProgressService);
        User existingUser = new User("google-existing", "existing@example.com", "김한비");
        ReflectionTestUtils.setField(existingUser, "id", 20L);

        given(userRepository.findByGoogleId("google-existing")).willReturn(Optional.of(existingUser));

        User result = service.provisionOAuthUser("google-existing", "existing@example.com", "김한비");

        verify(userRepository, never()).save(any(User.class));
        verify(signupProgressService, never()).initializeFor(any(Long.class));
        assertThat(result).isSameAs(existingUser);
    }
}
