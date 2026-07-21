package com.careerdungeon.global.security;

import com.careerdungeon.domain.auth.oauth.CustomOAuth2UserService;
import com.careerdungeon.domain.auth.oauth.OAuth2SuccessHandler;
import com.careerdungeon.global.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // #65 -> #66 -> #75 -> #76 로 baseUri/redirect-uri 불일치가 반복돼서, 실제 설정값과
    // 테스트(SecurityConfigTest)가 같은 상수를 보도록 분리해뒀다 — 문자열을 테스트에
    // 복붙하면 이 값만 바뀌고 테스트는 그대로 통과하는 회귀를 못 잡는다.
    static final String OAUTH2_CALLBACK_BASE_URI = "/api/auth/oauth2/callback/*";

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          OAuth2SuccessHandler oAuth2SuccessHandler,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // 뱃지 이미지(src/main/resources/static/badges/**)는 <img> 태그로 직접
                        // 로드되어 Authorization 헤더를 실어 보낼 수 없다. 이미지 4장은 고정된
                        // 공개 자산(개인정보 아님)이라 이 경로만 최소 범위로 공개한다(이슈 #105,
                        // ADR-015). /api/** 인증 정책은 그대로 유지된다.
                        .requestMatchers("/badges/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                                    "AUTHENTICATION_REQUIRED",
                                    "인증이 필요합니다.",
                                    HttpStatus.UNAUTHORIZED.value()));
                        })
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(ae -> ae.baseUri("/api/auth/oauth2"))
                        .redirectionEndpoint(re -> re.baseUri(OAUTH2_CALLBACK_BASE_URI))
                        .userInfoEndpoint(ui -> ui.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
