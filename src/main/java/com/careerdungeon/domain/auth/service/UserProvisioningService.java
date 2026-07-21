package com.careerdungeon.domain.auth.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.progress.service.SignupProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final SignupProgressService signupProgressService;

    public UserProvisioningService(
            UserRepository userRepository,
            SignupProgressService signupProgressService) {
        this.userRepository = userRepository;
        this.signupProgressService = signupProgressService;
    }

    @Transactional
    public User provisionOAuthUser(String googleId, String email, String name) {
        return userRepository.findByGoogleId(googleId)
                .orElseGet(() -> createUserWithInitialProgress(googleId, email, name));
    }

    private User createUserWithInitialProgress(String googleId, String email, String name) {
        User user = userRepository.save(new User(googleId, email, name));
        signupProgressService.initializeFor(user.getId());
        return user;
    }
}
