package com.careerdungeon.domain.auth.controller;

import com.careerdungeon.domain.auth.dto.UserResponse;
import com.careerdungeon.domain.auth.dto.UserUpdateRequest;
import com.careerdungeon.domain.auth.dto.UserUpdateResponse;
import com.careerdungeon.domain.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal Long userId) {
        return userService.getMe(userId);
    }

    @PatchMapping("/me")
    public UserUpdateResponse updateName(@AuthenticationPrincipal Long userId,
                                         @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateName(userId, request.name());
    }
}
