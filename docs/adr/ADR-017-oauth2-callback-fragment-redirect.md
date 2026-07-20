# ADR-017 — 로그인 콜백: accessToken을 URL fragment로 실어 프론트로 리다이렉트

- 상태: 제안
- 작성자: 표지민
- 작성일: 2026-07-20
- 관련 이슈/PR: #96, FE #3(INT2_Team3_Vibe_FE)

## 배경

구글 로그인 콜백(`OAuth2SuccessHandler`)이 로그인 성공 후 브라우저를 프론트로 돌려보내지
않고, 그 자리에서 `{accessToken, user}` JSON을 그대로 응답해왔다. 브라우저는 여전히
백엔드 콜백 주소에 머물러 있어 사용자에게 리액트 앱 대신 날것의 JSON 텍스트가 보이고,
로그인 흐름이 끝나지 않는다. AU-001(구글 로그인 시작)은 `window.location.href` 이동으로
시작되는 순수 브라우저 리다이렉트 체인이라, 콜백도 마찬가지로 최종적으로 프론트 페이지로
리다이렉트되어야 한다. accessToken을 프론트에 어떻게 넘길지가 이 계약의 핵심이었다.

## 결정

`OAuth2SuccessHandler`가 로그인 성공 시 `{프론트 origin}/oauth/callback#accessToken={토큰}`
으로 302 리다이렉트한다. 토큰은 쿼리 파라미터(`?accessToken=`)가 아니라 URL fragment(`#`)에
싣는다 — fragment는 브라우저가 어떤 후속 요청(리다이렉트, 정적 자원 요청 등)에도 서버로
전송하지 않으므로, 서버 접근 로그·Referer 헤더에 토큰이 남지 않는다.

프론트는 `/oauth/callback` 경로에 전용 페이지를 두고, 페이지 로드 시 `location.hash`에서
`accessToken`을 파싱해 저장한 뒤 `history.replaceState`로 URL에서 토큰을 지우고 메인
페이지로 이동해야 한다(표지민이 프론트 쪽도 함께 구현).

리다이렉트 대상 origin은 새 설정을 추가하지 않고 기존 `cors.allowed-origins`(CORS 허용
origin과 동일한 값)를 재사용한다. 여러 origin이 설정된 경우 첫 번째 값을 사용한다.

`user` 정보(이름/이메일)는 더 이상 콜백 응답에 포함하지 않는다. 프론트는 토큰 저장 후
`GET /api/users/me`(UP-003, PR #100)를 별도로 호출해 조회한다.

## 핵심 근거

- **fragment vs 쿼리 파라미터**: 쿼리 파라미터는 서버 접근 로그에 그대로 남고 Referer로
  제3자 사이트에 노출될 수 있다. fragment는 브라우저 로컬에만 존재하고 어떤 HTTP 요청에도
  실리지 않는다.
- **1회용 코드 교환 방식(대안) 대비 구현 비용**: accessToken이 이미 30분 단기 토큰으로
  설계되어 있어(ADR-012), fragment에 짧게 노출되는 리스크가 상대적으로 작다. 3주 MVP
  일정상 코드 교환용 임시 저장소·만료 로직·별도 API까지 새로 만드는 비용을 지금 감당하기
  어렵다.
- **콜백 응답에서 user 정보 제거**: 이미 GET /api/users/me(PR #100, 이슈 #97)가 있어
  콜백 응답에 사용자 정보를 욱여넣을 필요가 없다 — 책임을 분리하면 콜백 핸들러가
  단순해지고, "새로고침 시 유저 정보 재조회"와 "최초 로그인 시 유저 정보 조회"가 같은
  경로를 타게 되어 프론트 코드 중복도 줄어든다.

## 대안 및 반려

- **1회용 코드 교환 방식(백엔드가 임시 코드만 리다이렉트로 보내고, 프론트가 별도 API로
  진짜 토큰을 교환)** — accessToken이 URL에 전혀 노출되지 않아 더 안전하지만, 임시 코드
  저장소(만료 처리 포함)와 교환용 API를 새로 만들어야 해서 이번 PR 범위 대비 비용이
  크다. accessToken이 이미 단기 토큰이라는 점을 고려해 이번 스코프에서는 반려. 이후
  보안 요구 수준이 높아지면 재검토한다.
- **accessToken을 응답 쿠키로 발급(HttpOnly)** — refreshToken과 같은 방식으로 통일할
  수 있지만, accessToken은 프론트가 매 요청마다 `Authorization: Bearer` 헤더로 직접
  실어 보내는 기존 계약(전역 인증 필터, `api/client.ts`)과 어긋난다. 이 계약을 바꾸려면
  프론트 요청 계층 전체와 `JwtAuthenticationFilter`를 함께 바꿔야 해서 반려.

## 결과 (기대)

- 실제 구글 로그인이 끝까지 동작하고, 사용자는 로그인 완료 후 프론트 메인 페이지에
  도착한다.
- `docs/api/api-spec.md` AU-002 응답 계약이 JSON 예시에서 리다이렉트 설명으로 바뀐다.
- `LoginResponse` DTO(콜백 JSON 응답 전용)는 더 이상 쓰이지 않아 제거한다.

## 관련 문서

- `docs/adr/ADR-006-google-oauth2-over-self-auth.md` — OAuth2 채택 근거
- `docs/adr/ADR-012-refresh-token-httponly-cookie.md` — accessToken을 단기 토큰으로 설계한 배경
- `docs/api/api-spec.md` AU-002, UP-003
- FE #3(`INT2_Team3_Vibe_FE`) — 이 결정을 촉발한 프론트 이슈
