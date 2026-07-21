package com.careerdungeon.domain.auth.oauth;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.auth.service.AuthService;
import com.careerdungeon.domain.auth.service.RefreshTokenCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    // 로그인 성공 후 브라우저를 이 프론트 경로로 돌려보낸다. accessToken은 쿼리 파라미터가 아니라
    // URL fragment(#)에 실어서 보낸다 — fragment는 브라우저가 서버로 절대 전송하지 않아
    // 액세스 로그/Referer에 토큰이 남지 않는다(이슈 #96). 프론트는 이 경로에서 fragment를
    // 파싱해 저장한 뒤 즉시 history를 정리(replaceState)하고 메인페이지로 이동해야 한다.
    private static final String FRONTEND_CALLBACK_PATH = "/oauth/callback";

    private final AuthService authService;
    private final RefreshTokenCookieFactory cookieFactory;
    private final String frontendOrigin;

    public OAuth2SuccessHandler(AuthService authService,
                                 RefreshTokenCookieFactory cookieFactory,
                                 @Value("${cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
        this.frontendOrigin = allowedOrigins.split(",")[0].trim();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        AuthService.LoginResult result = authService.login(user);

        ResponseCookie cookie = cookieFactory.create(result.refreshTokenValue());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        String encodedToken = URLEncoder.encode(result.accessToken(), StandardCharsets.UTF_8);
        String redirectUrl = frontendOrigin + FRONTEND_CALLBACK_PATH + "#accessToken=" + encodedToken;
        response.sendRedirect(redirectUrl);
    }
}
