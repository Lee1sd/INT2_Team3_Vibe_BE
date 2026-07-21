# ADR-012 — Refresh Token 저장 전략: HttpOnly 쿠키 + Access Token 분리

- 상태: 승인 (이슈 #117로 로컬 프로필 값 추가 — 아래 "후속 변경" 참고)
- 작성자: 표지민
- 작성일: 2026-07-14
- 관련 이슈/PR: #35, PR #37, 이슈 #117

## 배경

Google OAuth2 로그인(ADR-006) 구현 시 두 가지 토큰을 어디에 저장할지 결정해야 했다.

- **Access Token**: 짧은 만료(30분), 모든 API 요청에 사용
- **Refresh Token**: 긴 만료(7일), Access Token 재발급에만 사용

저장 위치 선택에서 보안과 사용 편의성 사이의 트레이드오프가 존재했다.
특히 Refresh Token은 만료가 길어 탈취 시 피해가 크므로 저장 방식이 핵심 결정이었다.

## 결정

- **Access Token**: 서버가 JSON 응답 body에 담아 반환 → 프론트엔드가 메모리(JS 변수)에 보관 후 매 요청 `Authorization: Bearer` 헤더로 전송
- **Refresh Token**: 서버가 `Set-Cookie` 헤더로 발급 → `HttpOnly`, `Secure`, `SameSite=None`, `Path=/`, 만료 7일
- **logout/refresh 엔드포인트**: JWT 인증 없이 쿠키만으로 동작 (`permitAll`). 단, 쿠키 없는 요청은 서비스 레이어에서 early return 처리.
- **SameSite=None**: 프론트·백이 다른 도메인으로 배포되는 환경에 대응. `Secure=true`와 반드시 함께 사용.

## 핵심 근거

Refresh Token을 HttpOnly 쿠키에 두면 JavaScript에서 접근 자체가 불가능해 XSS 공격으로 탈취할 수 없다.
3주 MVP 일정상 별도 토큰 관리 인프라(Redis 등)를 추가하지 않고 DB 저장으로 충분히 revoke 제어가 가능하다.
Access Token은 JS에서 읽어야 헤더에 담을 수 있으므로 쿠키가 아닌 메모리 보관이 불가피하며, 짧은 만료 시간(30분)이 위험을 제한한다.

## 대안 및 반려

- **localStorage에 Refresh Token 저장** — `document.localStorage`는 JS로 읽을 수 있어 XSS로 바로 탈취 가능. 만료 7일 토큰을 여기에 두는 것은 허용 불가. 반려.
- **sessionStorage에 Refresh Token 저장** — 탭 닫으면 사라지고 JS 접근 가능. localStorage와 같은 이유로 반려.
- **Access Token도 HttpOnly 쿠키에 저장** — JS에서 읽을 수 없으므로 `Authorization` 헤더 조립 불가. Bearer 방식을 포기해야 하며, 모든 API 요청 흐름을 cookie 기반으로 재설계해야 함. 3주 일정 내 불가. 반려.
- **SameSite=Strict** — 프론트·백 다른 도메인 배포 시 쿠키가 전송되지 않아 refresh/logout이 동작하지 않음. 반려.
- **SameSite=Lax** — GET 요청에는 쿠키 전달되지만 POST(refresh, logout)에는 cross-site에서 전달 안 됨. 반려.
- **Redis로 Refresh Token 서버 측 저장** — 빠른 조회와 자동 만료 관리가 장점이나, 추가 인프라 비용과 설정 복잡도 증가. DB 저장 + 원자적 UPDATE(`revokeByTokenHash`)로 동시성 문제까지 해결되므로 MVP에서 불필요. 반려.
- **logout에 JWT 인증 요구** — Access Token 만료(30분) 상태에서 로그아웃 시도 시 401 반환 → UX 단절. Refresh Token 쿠키가 존재하면 사실상 인증된 사용자이므로 쿠키 기반 검증으로 충분. 반려.

## 결과

- XSS 공격으로 Refresh Token을 탈취할 수 없는 구조가 됨
- 프론트엔드는 Refresh Token을 직접 다루지 않아도 됨 (브라우저가 자동으로 쿠키 전송)
- Access Token 만료 후에도 쿠키로 logout 가능 → UX 단절 없음
- 크로스 도메인 배포 환경에서도 refresh/logout 정상 동작

## 후속 변경 (이슈 #117, 2026-07-21)

이 ADR을 쓸 당시엔 `Secure`/`SameSite` 값을 프로필 구분 없이 고정값으로 정했다
(위 "결정"의 `HttpOnly`/`Secure`/`SameSite=None`). 그런데 로컬 개발 환경은
`http://localhost:3000`↔`http://localhost:8080`으로 둘 다 평문 HTTP라, `Secure=true`
쿠키는 브라우저가 애초에 저장/전송하지 않는다 — 로그인 직후 새로고침하거나
`POST /api/auth/refresh`를 호출하면 실패하는 원인이었다.

- **local**: `Secure=false`, `SameSite=Lax`로 전환. 로컬은 HTTPS가 아니므로
  `Secure=true`를 쓸 수 없다.
- **prod**: 위 "결정"에서 이미 정한 `Secure=true`, `SameSite=None`을 그대로 유지한다
  (이 부분은 새로운 결정이 아니라 기존 결정 재확인 — 아래 "대안 및 반려"의
  `SameSite=Strict`/`Lax` 반려 사유가 그대로 적용된다).
- 구현은 `RefreshTokenCookieFactory`(`auth.cookie.secure`/`auth.cookie.same-site`
  프로퍼티) 하나로 통합했고, `Secure=false`+`SameSite=None` 조합(브라우저가 거부하는
  조합)은 기동 시점에 fail-fast로 막는다.
- `docs/requirements/security-design.md`가 이 결정과 무관하게 프로필 구분 없이
  `Strict`로 기재되어 있던 것은 이 ADR이 처음 승인됐을 때(2026-07-14) 함께
  갱신되지 않은 문서 SSOT 불일치였다 — 이번에 프로필별 정책으로 정정했다.

## 관련 문서

- [ADR-006](ADR-006-google-oauth2-over-self-auth.md) — Google OAuth2 채택 배경
- `docs/api-spec.md` AU-001~004 — 인증 API 명세
- `docs/requirements/security-design.md` §1 — 프로필별 쿠키 정책 (이슈 #117로 정정)
- `src/main/java/com/careerdungeon/domain/auth/oauth/OAuth2SuccessHandler.java` — 쿠키 발급
- `src/main/java/com/careerdungeon/domain/auth/service/RefreshTokenCookieFactory.java` — 프로필별 쿠키 생성 (이슈 #117)
- `src/main/java/com/careerdungeon/domain/auth/service/AuthService.java` — 토큰 순환·revoke
