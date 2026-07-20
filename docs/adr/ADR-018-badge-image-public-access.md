# ADR-018 — 뱃지 정적 이미지 경로(/badges/**)를 인증 없이 공개

- 상태: 제안
- 작성자: 표지민
- 작성일: 2026-07-20
- 관련 이슈/PR: #105, ADR-015

## 배경

ADR-015(최용성)에서 뱃지 이미지를 `src/main/resources/static/badges/**`에 애플리케이션
정적 리소스로 배포하고, `Badge.imageUrl`을 `/badges/Level1.png`~`Level4.png` 상대
경로로 확정했다. 그런데 현재 `SecurityConfig`는 `/api/auth/**` 외 모든 요청을 인증
대상으로 처리하고, JWT는 `Authorization` 헤더에서만 읽는다. 브라우저의 일반 `<img>`
태그 요청은 이 헤더를 실어 보낼 수 없어, 지금 계약만으로는 프론트가 뱃지 이미지를
화면에 직접 표시할 수 없다(ADR-015가 이 결정을 이 이슈로 분리해뒀다).

## 결정

`SecurityConfig`의 `authorizeHttpRequests`에 `.requestMatchers("/badges/**").permitAll()`을
추가해 이 경로만 인증 없이 공개한다. `/api/**`를 포함한 나머지 모든 경로는 기존 인증
정책(`anyRequest().authenticated()`)을 그대로 유지한다.

## 핵심 근거

- 뱃지 이미지 4장은 **개인정보가 아니고, 모든 사용자가 동일하게 공유하는 고정된 공개
  자산**이다 — 특정 사용자만 봐야 하는 데이터가 아니라 인증으로 보호할 이유가 없다.
- 인증 fetch + Blob URL 방식(대안)은 프론트에서 매 이미지마다 별도 JS 요청과 메모리
  관리가 필요해 `<img src="...">` 한 줄로 끝나는 지금 방식보다 구현·유지보수 비용이
  크다. 이미지가 4장뿐이고 절대 안 바뀌는 고정 자산이라는 점을 고려하면 그 비용을
  들일 이유가 없다.
- `/badges/**`만 정확히 열어주므로 `/api/**`의 나머지 인증 경계는 전혀 약화되지 않는다
  — `BadgeImagePublicAccessTest`로 실제 보안 필터 체인을 태워 이 경계를 검증했다.

## 대안 및 반려

- **인증된 fetch로 이미지를 받아 Blob URL로 표시** — accessToken을 붙여 이미지를
  요청할 수 있지만, 프론트가 이미지마다 별도 요청·캐싱·해제 로직을 관리해야 한다.
  고정된 공개 자산 4장에 비해 과한 구현이라 반려.
- **CDN/S3 절대 URL로 전환 후 그쪽에서 공개 정책 관리** — ADR-015가 이미 반려한
  대안과 같은 맥락(버킷·도메인·캐시 정책 미확정, 3주 MVP에 새 인프라 추가 부담)이라
  이번에도 반려.

## 결과 (기대)

- 프론트가 `<img src="{API origin}/badges/Level1.png">`처럼 별도 인증 로직 없이
  뱃지 이미지를 바로 표시할 수 있다.
- `/api/**`(예: `/api/badges/me`)는 인증 없이 호출하면 여전히 401을 반환한다.
- `BadgeImagePublicAccessTest`가 이 경계를 보안 필터 활성화 상태로 검증한다.

## 관련 문서

- [ADR-015](ADR-015-badge-assets-served-by-application.md) — 뱃지 자산을 정적
  리소스로 배포하기로 한 원 결정, 이 이슈를 후속으로 분리해둔 배경
- `docs/api/api-spec.md` BG-001
- `src/main/java/com/careerdungeon/global/security/SecurityConfig.java`
