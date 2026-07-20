package com.careerdungeon.domain.auth.controller;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PR #100 리뷰(이건희) 반영 — {@code UserServiceTest}는 {@code getMe(userId)}를 값으로 직접
 * 호출해 {@code @AuthenticationPrincipal}이 실제 JWT에서 userId를 제대로 추출하는지는
 * 검증하지 못한다. 여기서는 실제 보안 필터 체인과 발급된 JWT로 컨트롤러까지 전부 태워
 * 응답 JSON 매핑까지 함께 확인한다. 인증 없는 요청의 401 처리는
 * {@code BadgeImagePublicAccessTest} 등 공통 시큐리티 테스트가 이미 커버한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Test
    void getMe_withValidJwt_returnsAuthenticatedUsersOwnInfo() throws Exception {
        User user = userRepository.save(new User("google-me-test", "me@example.com", "홍길동"));
        String token = jwtProvider.generateAccessToken(user.getId());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("me@example.com"));
    }
}
