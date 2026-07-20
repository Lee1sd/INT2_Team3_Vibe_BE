package com.careerdungeon.domain.auth.oauth;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.service.SignupProgressService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final SignupProgressService signupProgressService;

    public CustomOAuth2UserService(
            UserRepository userRepository,
            SignupProgressService signupProgressService) {
        this.userRepository = userRepository;
        this.signupProgressService = signupProgressService;
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String googleId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");

        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> createUserWithInitialProgress(googleId, email, name));

        return new CustomOAuth2User(user, attributes);
    }

    private User createUserWithInitialProgress(String googleId, String email, String name) {
        User user = userRepository.save(new User(googleId, email, name));
        signupProgressService.initializeFor(user.getId());
        return user;
    }
}
