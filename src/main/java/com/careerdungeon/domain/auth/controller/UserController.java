package com.careerdungeon.domain.auth.controller;

import com.careerdungeon.domain.auth.dto.ProfileImageResponse;
import com.careerdungeon.domain.auth.dto.UserResponse;
import com.careerdungeon.domain.auth.dto.UserUpdateRequest;
import com.careerdungeon.domain.auth.dto.UserUpdateResponse;
import com.careerdungeon.domain.auth.service.RefreshTokenCookieFactory;
import com.careerdungeon.domain.auth.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final RefreshTokenCookieFactory cookieFactory;

    public UserController(UserService userService, RefreshTokenCookieFactory cookieFactory) {
        this.userService = userService;
        this.cookieFactory = cookieFactory;
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

    // 마이페이지 프로필 이미지 업로드/교체 (UP-004, ADR-018). MIME/용량 검증은
    // ProfileImageStorageService가 담당한다.
    @PostMapping(value = "/me/photo", consumes = "multipart/form-data")
    public ProfileImageResponse uploadProfileImage(@AuthenticationPrincipal Long userId,
                                                     @RequestParam("photo") MultipartFile photo) {
        return userService.updateProfileImage(userId, photo);
    }

    // 프로필 이미지 제거 (UP-005). 이미지가 없어도 200으로 멱등하게 응답한다.
    @DeleteMapping("/me/photo")
    public Map<String, String> deleteProfileImage(@AuthenticationPrincipal Long userId) {
        userService.removeProfileImage(userId);
        return Map.of("message", "프로필 이미지가 삭제되었습니다");
    }

    // 회원 탈퇴(전체 즉시 삭제, ADR-016). refreshToken 쿠키도 로그아웃과 동일하게 즉시 만료시켜
    // 탈퇴 직후 /api/auth/refresh로 새 accessToken을 못 받게 막는다.
    @DeleteMapping("/me")
    public Map<String, String> withdraw(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        userService.withdraw(userId);

        ResponseCookie expiredCookie = cookieFactory.expired();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

        return Map.of("message", "회원 탈퇴가 완료되었습니다");
    }
}
