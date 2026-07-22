package com.careerdungeon.domain.progress.controller;

import com.careerdungeon.domain.progress.dto.UserBadgeListResponse;
import com.careerdungeon.domain.progress.dto.UserBadgeResponse;
import com.careerdungeon.domain.progress.dto.UserProgressResponse;
import com.careerdungeon.domain.progress.exception.UserProgressNotFoundException;
import com.careerdungeon.domain.progress.service.BadgeQueryService;
import com.careerdungeon.domain.progress.service.ProgressQueryService;
import com.careerdungeon.global.security.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UM-001과 BG-001의 인증 사용자 응답 및 공통 예외 직렬화를 검증한다. */
@WebMvcTest({UserProgressController.class, BadgeController.class})
@AutoConfigureMockMvc(addFilters = false)
class ProgressQueryControllerTest {

    private static final long USER_ID = 7L;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProgressQueryService progressQueryService;

    @MockitoBean
    BadgeQueryService badgeQueryService;

    @MockitoBean
    JwtProvider jwtProvider;

    /** 실제 JWT 필터가 설정하는 Long 사용자 principal을 테스트에 재현한다. */
    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
    }

    /** 테스트 사이에 인증 정보가 공유되지 않도록 보안 컨텍스트를 정리한다. */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** UM-001 응답의 최상위 필드명과 숫자 형식을 검증한다. */
    @Test
    @DisplayName("GET /api/users/me/progress는 현재 해금 레벨과 게이지를 반환한다")
    void getMyProgressReturnsCurrentState() throws Exception {
        given(progressQueryService.getMyProgress(USER_ID))
                .willReturn(new UserProgressResponse(2, 30));

        mockMvc.perform(get("/api/users/me/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unlockedLevel").value(2))
                .andExpect(jsonPath("$.progressGauge").value(30));

        verify(progressQueryService).getMyProgress(USER_ID);
    }

    /** 진행도 미생성 예외가 확정된 공통 에러 계약으로 반환되는지 검증한다. */
    @Test
    @DisplayName("진행도 상태가 없으면 PROGRESS_NOT_FOUND 404를 반환한다")
    void getMyProgressReturnsNotFoundContract() throws Exception {
        given(progressQueryService.getMyProgress(USER_ID))
                .willThrow(new UserProgressNotFoundException(USER_ID));

        mockMvc.perform(get("/api/users/me/progress"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROGRESS_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    /** BG-001의 뱃지 식별자와 UTC Instant 직렬화 형식을 검증한다. */
    @Test
    @DisplayName("GET /api/badges/me는 획득 뱃지와 획득 시각을 반환한다")
    void getMyBadgesReturnsAcquiredBadges() throws Exception {
        UserBadgeResponse badge = new UserBadgeResponse(
                11L,
                1,
                "프로그래머쓱 LEVEL 1",
                "https://int-team3.s3.ap-northeast-2.amazonaws.com/badges/Level1.png?X-Amz-Signature=test",
                Instant.parse("2026-07-20T01:02:03Z"));
        given(badgeQueryService.getMyBadges(USER_ID))
                .willReturn(new UserBadgeListResponse(List.of(badge)));

        mockMvc.perform(get("/api/badges/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badges[0].badgeId").value(11))
                .andExpect(jsonPath("$.badges[0].stage").value(1))
                .andExpect(jsonPath("$.badges[0].name").value("프로그래머쓱 LEVEL 1"))
                .andExpect(jsonPath("$.badges[0].imageUrl").value(
                        "https://int-team3.s3.ap-northeast-2.amazonaws.com/badges/Level1.png?X-Amz-Signature=test"))
                .andExpect(jsonPath("$.badges[0].acquiredAt").value("2026-07-20T01:02:03Z"));

        verify(badgeQueryService).getMyBadges(USER_ID);
    }

    /** 아직 획득한 뱃지가 없는 정상 사용자의 빈 배열 계약을 검증한다. */
    @Test
    @DisplayName("획득 뱃지가 없으면 빈 badges 배열을 반환한다")
    void getMyBadgesReturnsEmptyArray() throws Exception {
        given(badgeQueryService.getMyBadges(USER_ID))
                .willReturn(new UserBadgeListResponse(List.of()));

        mockMvc.perform(get("/api/badges/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badges").isArray())
                .andExpect(jsonPath("$.badges").isEmpty());
    }

    /** seed에 저장한 상대 URL이 실제 Spring 정적 리소스 경로로 연결되는지 검증한다. */
    @Test
    @DisplayName("/badges/Level1.png는 PNG 정적 자산으로 제공된다")
    void badgeImageUrlResolvesToStaticPng() throws Exception {
        mockMvc.perform(get("/badges/Level1.png"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType(MediaType.IMAGE_PNG));
    }
}
