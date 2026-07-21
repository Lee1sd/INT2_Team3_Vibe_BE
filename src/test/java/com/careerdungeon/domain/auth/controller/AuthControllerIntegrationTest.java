package com.careerdungeon.domain.auth.controller;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.domain.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이슈 #117 — refresh/logout이 실제 HTTP 응답에서 RefreshTokenCookieFactory를 거쳐
 * test(=local과 동일한 기본값) 프로필 설정대로 Secure 없음 + SameSite=Lax 쿠키를
 * 내려주는지 MockMvc로 끝까지 확인한다. UserControllerIntegrationTest와 동일하게
 * 실제 보안 필터 체인/DB(H2)를 태운다.
 *
 * 클래스 레벨 {@code @Transactional}로 각 테스트가 만든 user/refresh_token을 테스트가
 * 끝나면 자동 롤백한다 — 이 프로젝트의 H2 테스트 스키마(ddl-auto: create-drop)는
 * refresh_tokens→users FK에 CASCADE가 없어(V11 마이그레이션은 MySQL 대상), 여기서 만든
 * refresh_token이 롤백 없이 남으면 다른 테스트 클래스(예: InterviewControllerIntegrationTest)의
 * {@code userRepository.deleteAll()}이 FK 위반으로 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AuthService authService;

    @Autowired
    UserRepository userRepository;

    @Test
    void refresh_withValidCookie_returnsNewCookieWithLocalTestCookieAttributes() throws Exception {
        User user = userRepository.save(new User("google-refresh-test", "refresh@example.com", "홍길동"));
        AuthService.LoginResult loginResult = authService.login(user);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", loginResult.refreshTokenValue())))
                .andExpect(status().isOk())
                .andReturn();

        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("refreshToken=");
        assertThat(setCookieHeader).doesNotContain("Secure");
        assertThat(setCookieHeader).contains("SameSite=Lax");
    }

    @Test
    void logout_withValidCookie_returnsExpiredCookieWithLocalTestCookieAttributes() throws Exception {
        User user = userRepository.save(new User("google-logout-test", "logout@example.com", "김철수"));
        AuthService.LoginResult loginResult = authService.login(user);

        MvcResult result = mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refreshToken", loginResult.refreshTokenValue())))
                .andExpect(status().isOk())
                .andReturn();

        String setCookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).isNotNull();
        assertThat(setCookieHeader).contains("refreshToken=;");
        assertThat(setCookieHeader).contains("Max-Age=0");
        assertThat(setCookieHeader).doesNotContain("Secure");
        assertThat(setCookieHeader).contains("SameSite=Lax");
    }
}
