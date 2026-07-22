package com.careerdungeon.domain.auth.controller;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.global.security.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PR #100 리뷰(이건희) 반영 — {@code UserServiceTest}는 {@code getMe(userId)}를 값으로 직접
 * 호출해 {@code @AuthenticationPrincipal}이 실제 JWT에서 userId를 제대로 추출하는지는
 * 검증하지 못한다. 여기서는 실제 보안 필터 체인과 발급된 JWT로 컨트롤러까지 전부 태워
 * 응답 JSON 매핑까지 함께 확인한다. 인증 없는 요청의 401 처리는
 * {@code BadgeImagePublicAccessTest} 등 공통 시큐리티 테스트가 이미 커버한다.
 *
 * 클래스 레벨 {@code @Transactional}로 각 테스트가 만든 user를 테스트가 끝나면 자동
 * 롤백한다 — 이슈 #117에서 추가한 withdraw 테스트가 만드는 상태가 다른 테스트 클래스의
 * {@code userRepository.deleteAll()}과 충돌하지 않게 하기 위함이다
 * ({@code AuthControllerIntegrationTest} 클래스 주석 참고).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    // 이슈 #98/ADR-020 — 실제 AWS 호출 없이 컨트롤러→서비스→DB(H2) 전체 경로를 검증하기
    // 위해 S3Client/S3Presigner만 목으로 대체한다. S3Config가 만드는 실제 빈은 자격증명이
    // 없어도 생성은 되지만(지연 연결), 실제 API 호출(putObject 등)은 네트워크가 필요하다.
    @MockitoBean
    S3Client s3Client;

    @MockitoBean
    S3Presigner s3Presigner;

    @Test
    void getMe_withValidJwt_returnsAuthenticatedUsersOwnInfo() throws Exception {
        User user = userRepository.save(new User("google-me-test", "me@example.com", "홍길동"));
        String token = jwtProvider.generateAccessToken(user.getId());

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.photoUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    // 이슈 #98/ADR-020 — 업로드 성공 시 UP-004 응답에 photoUrl이 담기고, 이어지는
    // GET /api/users/me(UP-003)에도 같은(재생성된) photoUrl이 포함되는지 확인한다.
    @Test
    void uploadProfileImage_thenGetMe_includesPresignedPhotoUrl() throws Exception {
        User user = userRepository.save(new User("google-photo-test", "photo@example.com", "박민수"));
        String token = jwtProvider.generateAccessToken(user.getId());

        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        given(presigned.url()).willReturn(
                URI.create("https://bucket.s3.amazonaws.com/profile-images/" + user.getId() + "/a.jpg?X-Amz-Signature=abc").toURL());
        given(s3Presigner.presignGetObject(any(software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .willReturn(presigned);

        MockMultipartFile file = new MockMultipartFile("photo", "me.jpg", "image/jpeg", "dummy-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value(
                        "https://bucket.s3.amazonaws.com/profile-images/" + user.getId() + "/a.jpg?X-Amz-Signature=abc"));

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value(
                        "https://bucket.s3.amazonaws.com/profile-images/" + user.getId() + "/a.jpg?X-Amz-Signature=abc"));
    }

    @Test
    void uploadProfileImage_withoutJwt_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("photo", "me.jpg", "image/jpeg", "dummy-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/users/me/photo").file(file))
                .andExpect(status().isUnauthorized());
    }

    // 이슈 #117 — withdraw()가 RefreshTokenCookieFactory.expired()를 거쳐 test(=local과
    // 동일한 기본값) 프로필 설정대로 Secure 없음 + SameSite=Lax인 만료 쿠키를 내려주는지 확인한다.
    @Test
    void withdraw_withValidJwt_returnsExpiredCookieWithLocalTestCookieAttributes() throws Exception {
        User user = userRepository.save(new User("google-withdraw-test", "withdraw@example.com", "이영희"));
        String token = jwtProvider.generateAccessToken(user.getId());

        MvcResult result = mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + token))
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
