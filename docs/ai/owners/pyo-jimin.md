---
owner: pyo-jimin
domain: "④ 인증 + ⑤ 인프라 + ⑥ 프론트 + 공통코드"
paths:
  - "src/main/java/com/careerdungeon/domain/auth/**"
  - "src/main/java/com/careerdungeon/global/**"
  - "(프론트엔드 리포지토리 — 별도 저장소)"
team: CareerDungeon_Backend
---

# Owner — 표지민 (인증 · 인프라 · 프론트 · 공통코드)

> ⚠️ 이 파일은 "AI 면접관 페르소나"와 무관합니다. 이 파일은 **코드 담당자(표지민)의
> 작업 규칙**입니다.

> ⚠️ **겸임 과다 경고**: 요구사항명세서 CM-002에 "공통코드 오너=표지민, 겸임 과다 우려,
> 팀 재확인 필요"라고 이미 표시되어 있습니다. 아래 "위임 규칙"은 표지민이 매번 병목이 되지
> 않도록 하네스 차원에서 방어하기 위한 것입니다. 팀은 이 규칙이 실제로 지켜지는지 주간
> 회고에서 확인하세요.

## 역할 한 줄

Google OAuth2 로그인, JWT 발급/검증, 전역 보안 정책(`global.security`), 공통 응답
포맷/전역 예외 처리(`global.exception`, `global.common`), 외부 연동·재시도 설정
(`global.config`), 도메인 독립 유틸(`global.util`), 그리고 프론트엔드(별도 리포지토리) 전체.

## 수정 가능 경로

```
src/main/java/com/careerdungeon/domain/auth/**
src/main/java/com/careerdungeon/global/**
```

프론트엔드 코드는 이 저장소 밖에 있습니다. 백엔드 API 계약 변경이 프론트에 영향을 준다면
`docs/api/api-spec.md`를 먼저 갱신하고 PR 본문에 "API 변경 사항"을 채우세요.

## 위임 규칙 (겸임 과부하 방지)

1. **공통 응답 포맷·전역 예외 계약은 1주차 중 확정합니다.** 확정되면
   `docs/api/api-contract.md`에 기록하고, 이후 이건희(①)/김한비(②)/최용성(③)은 이 계약만
   보고 각자 컨트롤러/예외를 구현합니다. 계약 확정 이후에는 표지민에게 매번 물어보지
   않아도 됩니다.
2. 계약을 변경해야 하는 상황이 생기면, 변경을 요청하는 쪽이 먼저
   `docs/api/api-contract.md`에 변경 제안을 적고 표지민의 승인을 받은 뒤 반영합니다 —
   표지민이 매번 코드를 대신 써주는 방식이 아니라 **계약 승인자** 역할로 한정합니다.
3. 인증이 필요한 각 도메인 API는 `@PreAuthorize` 등 선언적 방식으로 각 도메인이 직접
   적용하고, 전역 `SecurityFilterChain` 자체를 바꾸는 경우에만 표지민이 개입합니다.

## 손대지 말 것

- `domain/resume/**`, `domain/interview/**`, `domain/message/**`, `domain/persona/**`,
  `domain/judgment/**`, `domain/progress/**`의 **비즈니스 로직**. 전역 설정/보안/공통
  응답을 위해 이 경로의 컨트롤러/서비스를 참조하는 것은 가능하지만, 도메인 로직을 대신
  구현하지 않습니다.

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/api/api-spec.md` | `AU-001~004`(OAuth·JWT), `UP-001`(이름 수정), CM-001~003 |
| `docs/api/api-contract.md` | 공통 응답/에러 코드 — **이 문서를 확정하는 것이 표지민의 1주차 핵심 산출물** |
| `docs/requirements/functional-requirements.md` | FR-06(OAuth2 로그인), FR-07(프론트 5화면), FR-12(이름 개인화 중 PATCH API), CM-001~003 |
| `docs/erd/entity-definition.md` | `User`, `RefreshToken` 엔티티 |
| `docs/requirements/security-design.md` | 인증 보안 정책 값(Access 30분/Refresh **7일**, 로테이션), 파일/API 키/데이터 보안 |
| `docs/adr/ADR-006-google-oauth2-over-self-auth.md` | OAuth2 vs 자체 로그인 결정 근거 |
| `docs/requirements/open-questions.md` | #6 공통 응답 포맷 확정 여부 등 표지민 관련 미확정 항목 |

## 체크리스트 (표지민)

- [ ] JWT 시크릿·OAuth 클라이언트 시크릿이 코드/문서에 하드코딩되지 않고 `.env`로만
      관리되는가? (`.env.example` 갱신 포함)
- [ ] Access Token **30분** 만료, Refresh Token은 **7일**·`HttpOnly`/`Secure`/`Strict`
      쿠키로 별도 발급되는가? (FR-06, `AU-002`, 7일 만료는 `docs/requirements/security-design.md` §1이 근거 — FR-06 원문에는 Refresh 만료 기간이 없음)
- [ ] 리프레시 토큰 재사용/탈취 시나리오(회전, 만료 처리)가 정의되어 있는가? (`AU-003` 자동 재발급+로테이션)
- [ ] `docs/api/api-contract.md`(DRAFT)를 1주차 중 `✅ CONFIRMED`로 전환했는가 —
      다른 3명이 이 계약만 보고 작업하므로 늦어지면 전체가 흔들린다.
- [ ] CORS·보안 헤더 설정이 프론트 배포 도메인과 로컬 개발 환경 모두에서 동작하는가?
- [ ] 공통 응답 포맷 변경 시 이건희/김한비/최용성에게 공지했는가 (위임 규칙 §2)?
- [ ] S3 버킷이 퍼블릭 액세스 전체 차단 + IAM Role 기반 접근 제어로 되어 있는가? (`CM-003`)
- [ ] 공용 LLM API 키의 예산 상한(hard limit)이 실제로 설정되어 있는가? (`CM-001`, NFR-11 — 김한비와 공동 확인)
- [ ] 프론트 상태 연동(진행도 게이지, 뱃지 해금)이 백엔드 응답 필드(`UM-001`, `BG-001`)와
      1:1로 대응하는가?
