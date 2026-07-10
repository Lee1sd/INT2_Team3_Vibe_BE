# ADR-006 — 인증 방식: Google OAuth2 (자체 로그인 탈락)

- 상태: 승인 (기획서 v5.1, 16장)
- 근거 문서: 기획서 16장(ADR), 17장(보안 설계)

## 배경

유저별 레벨/해금 상태를 저장하려면 로그인이 필요하다. 후보는 (a) Google OAuth2,
(b) 자체 회원가입/로그인(이메일+비밀번호).

## 결정

**Google OAuth2**로 확정한다 (자체 로그인 탈락). `googleId`(sub 값), 이메일, 이름을
기반으로 `User` 도메인을 설계한다 (FR-06, `AU-001`~`AU-004`).

- 인증 흐름: 커스텀 경로로 OAuth2 시작 → 콜백에서 JWT 발급.
- Access Token: 30분 만료.
- Refresh Token: **7일**, `HttpOnly`/`Secure`/`Strict` 쿠키로 별도 발급, 자동
  재발급 + 로테이션 (기획서 17장 보안 설계 표. `docs/requirements/functional-requirements.md`
  FR-06에는 만료 기간이 명시돼 있지 않으므로 이 ADR과 17장을 근거로 **7일**을 채택한다).
- 로그아웃: 쿠키 삭제 + 서버 측 Refresh Token 무효화 (`AU-004`).

## 핵심 근거

- 자체 로그인은 회원가입 폼, 비밀번호 정책, 이메일 인증 등 추가 구현 범위가 넓다.
- OAuth2는 팀의 기존 경험을 재사용할 수 있어 공수를 최소화한다.

## 대안 및 반려

- **자체 로그인(이메일+비밀번호)** — 반려. 3주 일정에서 비밀번호 저장/검증/재설정
  흐름까지 구현하는 비용이 OAuth2보다 크다.
- **Kakao 로그인 등 추가 소셜 로그인** — 스트레치 골로 이관 (`FEAT-16`, MVP 범위 아님).

## 결과 (기대)

- `docs/ai/owners/pyo-jimin.md`가 이 인증 흐름 전체(회원 모델, 로그인/가입, 세션/토큰,
  인가)를 소유한다.
- Refresh Token 7일 만료, 로테이션, `HttpOnly`/`Secure`/`Strict` 쿠키 정책은
  `docs/requirements/security-design.md`에도 동일하게 기록해 SSOT를 유지한다.

## 관련 문서

- `docs/api/api-spec.md` — `AU-001`~`AU-004`
- `docs/requirements/security-design.md` — 인증 보안 표
- `docs/ai/owners/pyo-jimin.md` — 인증 도메인 체크리스트
