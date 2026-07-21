package com.careerdungeon.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * 이슈 #105 — 뱃지 정적 이미지(/badges/**)는 인증 없이 공개하되, 나머지 /api/** 인증
 * 정책은 그대로 유지되는지 실제 보안 필터 체인을 태워서 검증한다(@AutoConfigureMockMvc가
 * 필터를 걷어내지 않는 기본값을 그대로 씀).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BadgeImagePublicAccessTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("인증 없이 /badges/Level1.png에 접근할 수 있다")
    void badgeImage_accessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/badges/Level1.png"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증 없이 /api/badges/me를 호출하면 401이다 — /badges/** 공개가 /api/** 정책까지 풀어주지 않는다")
    void apiEndpoint_stillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/badges/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 뱃지 이미지를 요청하면 404다")
    void badgeImage_notFound_returns404() throws Exception {
        mockMvc.perform(get("/badges/NotExist.png"))
                .andExpect(status().isNotFound());
    }
}
