package com.careerdungeon.domain.auth.oauth;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.service.UserProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserProvisioningService userProvisioningService;

    @Mock
    private OAuth2UserRequest userRequest;

    @Test
    void loadUser_delegatesUserProvisioningAfterOAuthUserFetch() {
        OAuth2User googleUser = googleUser("google-1", "user@example.com", "김한비");
        CustomOAuth2UserService service = new TestableCustomOAuth2UserService(userProvisioningService, googleUser);
        User user = new User("google-1", "user@example.com", "김한비");
        ReflectionTestUtils.setField(user, "id", 20L);

        given(userProvisioningService.provisionOAuthUser("google-1", "user@example.com", "김한비"))
                .willReturn(user);

        OAuth2User result = service.loadUser(userRequest);

        verify(userProvisioningService).provisionOAuthUser("google-1", "user@example.com", "김한비");
        assertThat(((CustomOAuth2User) result).getUser()).isSameAs(user);
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
                UserProvisioningService userProvisioningService,
                OAuth2User oAuth2User) {
            super(userProvisioningService);
            this.oAuth2User = oAuth2User;
        }

        @Override
        protected OAuth2User fetchOAuth2User(OAuth2UserRequest userRequest) {
            return oAuth2User;
        }
    }
}
