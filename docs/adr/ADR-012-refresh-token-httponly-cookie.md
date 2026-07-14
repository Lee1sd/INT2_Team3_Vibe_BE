# ADR-012 — Refresh Token 저장 전략: HttpOnly 쿠키 + Access Token 분리

- 상태: 승인
- 작성자: 표지민
- 작성일: 2026-07-14
- 관련 이슈/PR: #35, PR #37

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

## 관련 문서

- [ADR-006](ADR-006-google-oauth2-over-self-auth.md) — Google OAuth2 채택 배경
- `docs/api-spec.md` AU-001~004 — 인증 API 명세
- `src/main/java/com/careerdungeon/domain/auth/oauth/OAuth2SuccessHandler.java` — 쿠키 발급
- `src/main/java/com/careerdungeon/domain/auth/service/AuthService.java` — 토큰 순환·revoke
