package com.careerdungeon.domain.auth.oauth;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.service.SignupProgressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SignupProgressService signupProgressService;

    @Mock
    private OAuth2UserRequest userRequest;

    @Test
    void loadUser_createsSignupProgressWhenGoogleUserDoesNotExist() {
        OAuth2User googleUser = googleUser("google-new", "new@example.com", "김한비");
        CustomOAuth2UserService service = new TestableCustomOAuth2UserService(
                userRepository, signupProgressService, googleUser);

        given(userRepository.findByGoogleId("google-new")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 10L);
            return user;
        });

        OAuth2User result = service.loadUser(userRequest);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getGoogleId()).isEqualTo("google-new");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(userCaptor.getValue().getName()).isEqualTo("김한비");
        verify(signupProgressService).initializeFor(10L);
        assertThat(((CustomOAuth2User) result).getUser().getId()).isEqualTo(10L);
    }

    @Test
    void loadUser_doesNotCreateSignupProgressWhenGoogleUserAlreadyExists() {
        OAuth2User googleUser = googleUser("google-existing", "existing@example.com", "김한비");
        CustomOAuth2UserService service = new TestableCustomOAuth2UserService(
                userRepository, signupProgressService, googleUser);
        User existingUser = new User("google-existing", "existing@example.com", "김한비");
        ReflectionTestUtils.setField(existingUser, "id", 20L);

        given(userRepository.findByGoogleId("google-existing")).willReturn(Optional.of(existingUser));

        OAuth2User result = service.loadUser(userRequest);

        verify(userRepository, never()).save(any(User.class));
        verify(signupProgressService, never()).initializeFor(any(Long.class));
        assertThat(((CustomOAuth2User) result).getUser()).isSameAs(existingUser);
    }

    private static OAuth2User googleUser(String googleId, String email, String name) {
        return new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("sub", googleId, "email", email, "name", name),
                "sub");
    }

    private static class TestableCustomOAuth2UserService extends CustomOAuth2UserService {

        private final OAuth2User oAuth2User;

        private TestableCustomOAuth2UserService(
                UserRepository userRepository,
                SignupProgressService signupProgressService,
                OAuth2User oAuth2User) {
            super(userRepository, signupProgressService);
            this.oAuth2User = oAuth2User;
        }

        @Override
        protected OAuth2User fetchOAuth2User(OAuth2UserRequest userRequest) {
            return oAuth2User;
        }
    }
}
