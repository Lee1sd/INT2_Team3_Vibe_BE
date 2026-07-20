package com.careerdungeon.domain.auth.oauth;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.service.UserProvisioningService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserProvisioningService userProvisioningService;

    public CustomOAuth2UserService(UserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = fetchOAuth2User(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String googleId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        User user = userProvisioningService.provisionOAuthUser(googleId, email, name);

        return new CustomOAuth2User(user, attributes);
    }

    protected OAuth2User fetchOAuth2User(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }
}
