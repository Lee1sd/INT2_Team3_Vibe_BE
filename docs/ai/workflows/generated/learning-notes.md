# 학습 노트 — 표지민

| # | Notion 제목 후보 | 한 줄 요약 | 발표 트러블 후보? (Yes/No) |
| --- | --- | --- | --- |
| 1 | 2026-07-28 \| OAuth2 콜백 와일드카드 누락 \| PR #76 | AntPathRequestMatcher 정확매치로 콜백 경로가 안 잡혀 2중 버그로 로그인 실패 | Yes |
| 2 | 2026-07-28 \| 로컬 Flyway 기동 실패 DB 재생성 \| 없음 | ddl-auto로 만든 기존 스키마와 Flyway 히스토리 부재 충돌 | Yes |

---

### NOTE 1

2026-07-28 | OAuth2 콜백 와일드카드 누락 | PR #76

## [학습 노트] SecurityConfig의 baseUri 정확매치 vs 와일드카드, 그리고 2중 버그

| 항목 | 내용 |
| --- | --- |
| **날짜** | 2026-07-28 |
| **Issue** | #75 |
| **PR** | #76 |
| **담당** | 표지민 (auth) |
| **한 줄 요약** | OAuth2 콜백이 `InvalidClientRegistrationIdException`으로 실패한 원인이 yml 하나가 아니라 SecurityConfig까지 겹친 2중 버그였음을 밝히고 고침 |

### 왜 고민을 시작했는가 (트리거)
- 팀원이 "SecurityConfig baseUri에 trailing slash가 붙어있다"는 걸 발견해 보고했고, 내가 올렸던 PR #66이 의미 없었던 건지 의심이 들어 조사를 시작함.
- 고민 1: PR #66이 정말 무의미했는지, 아니면 별개의 버그가 남아있는 건지
- 고민 2: 진짜 근본 원인이 정확히 어디인지 (yml vs SecurityConfig vs 둘 다)
- 고민 3: PR을 다시 올리기 전에 로컬에서 어떻게 직접 검증할지

### 무엇을 어떻게 고민했는가
- 고민 1: PR #66 이력을 다시 확인 — yml에 `{registrationId}`를 추가한 것 자체는 필요한 수정이었고 무의미하지 않았음. 다만 SecurityConfig 쪽에 독립적인 버그가 남아있었던 것.
- 고민 2: `AntPathRequestMatcher`가 `baseUri("/api/auth/oauth2/callback")`을 정확히 그 경로에만 매치시키고 `/callback/google`은 못 잡는다는 걸 확인. 여기에 Spring Security OAuth2 필터 체인 순서(`AuthorizationRequestRedirectFilter`가 `OAuth2LoginAuthenticationFilter`보다 먼저 요청을 가로챔)까지 함께 추적해서, 왜 `registrationId`가 "callback"으로 잘못 파싱되는지 정확한 경로를 규명함.
- 고민 3: 직접 Google 로그인을 시도해보기로 결정 — PR 올리기 전에 로컬 기동 후 실제 클릭 검증.

### 어떤 결정을 내렸는가
- 결정 1: yml 버그(#66)와 SecurityConfig 버그, 둘 다 독립적으로 실재했고 둘 다 고쳐야 완전히 해결된다고 결론.
- 결정 2: `baseUri`를 trailing slash가 아니라 와일드카드 `/api/auth/oauth2/callback/*`로 수정.
- 결정 3: PR #66이 이미 머지된 뒤 SecurityConfig 커밋이 push됐다는 걸 발견해, 새 Issue #75 + 별도 PR #76으로 분리 제출. 이후 실제 설정값과 `SecurityConfigTest`가 같은 문자열을 복붙하지 않고 같은 상수(`OAUTH2_CALLBACK_BASE_URI`)를 보도록 분리해 재발을 막음.

### 고민 요약 (복습용 표)
| # | 왜 시작 | 어떻게 고민 | 최종 결정 |
| --- | --- | --- | --- |
| 1 | PR #66이 무의미했는지 의심 | 이력 재확인, yml 수정은 유효했음을 확인 | 2개의 독립 버그가 겹친 것으로 결론 |
| 2 | 근본 원인 불명확 | AntPathRequestMatcher 매칭 규칙 + 필터 체인 순서 추적 | `/*` 와일드카드 필요 확인 |
| 3 | 재발 방지 필요 | 문자열 중복 대신 상수 공유 검토 | `OAUTH2_CALLBACK_BASE_URI` 상수로 설정·테스트 동기화 |

### 내가 이해한 흐름 (그림 말로)
1. 사용자가 Google 로그인 클릭 → `/api/auth/oauth2/{registrationId}`로 리다이렉트 (`AuthorizationRequestRedirectFilter` 처리)
2. Google 인증 후 콜백이 `/api/auth/oauth2/callback/google`로 돌아옴
3. `baseUri`가 정확매치(`/callback`)만 등록돼 있으면 이 하위 경로를 못 잡아 처리 흐름이 어긋남
4. `/*`로 바꾸면 `/callback/google`까지 매치되어 `OAuth2LoginAuthenticationFilter`가 정상적으로 콜백을 처리

### 주의 (팀 규칙·실수 방지)
- OAuth2 콜백류 경로는 "정확매치"와 "와일드카드"를 눈으로 구분하기 어렵다 — trailing slash(`/callback/`)와 와일드카드(`/callback/*`)는 완전히 다른 의미다.
- 설정값과 테스트가 같은 문자열을 각자 하드코딩하면, 설정만 바뀌고 테스트는 그대로 통과하는 회귀를 못 잡는다 — 상수 하나를 공유하게 만든다.
- PR을 머지 순서와 무관하게 "커밋 push 시점"을 확인하지 않으면, 리뷰까지 받은 수정이 실제로는 머지본에 없을 수 있다.

### 이번 PR 용어 3분 (새 것만, 최대 3개)
| 용어 | 쉬운 말 | 우리 문서 |
| --- | --- | --- |
| AntPathRequestMatcher | URL 패턴을 문자열로 비교해서 "이 경로 맞아?"를 판정하는 스프링 시큐리티 도구. `/*`가 없으면 정확히 그 글자만 맞아야 통과 | `SecurityConfig.java` 주석, `docs/requirements/security-design.md` |
| redirectionEndpoint | OAuth2 로그인에서 "구글이 로그인 끝내고 우리 서버로 돌려보내는 콜백 주소"를 등록하는 설정 지점 | `SecurityConfig.java` |
| 필터 체인 순서 | 요청이 여러 보안 필터를 순서대로 통과하는데, 어느 필터가 먼저 가로채느냐에 따라 같은 URL도 다르게 해석됨 | `docs/requirements/security-design.md` |

### 스스로 확인 질문 (가리고 답하기)
1. `baseUri`에 trailing slash를 붙이는 것과 `/*`를 붙이는 것의 실질적 차이는?
2. `AuthorizationRequestRedirectFilter`와 `OAuth2LoginAuthenticationFilter` 중 어느 게 먼저 요청을 가로채고, 그게 왜 `registrationId` 오인식으로 이어지는가?
3. PR #66이 머지된 뒤에도 SecurityConfig 수정이 반영 안 됐던 이유는?

### 24시간 뒤 한 줄 회고 (비워둠)
-

### 팀 ADR PR 필요?
- [ ] Yes — 이유:
- [x] No — 이유: 상수 공유 + 테스트 동기화라는 코드 레벨 가드로 재발 방지가 이미 됐고, 구조적 대안 검토가 필요한 결정은 아님.

---

### NOTE 2

2026-07-28 | 로컬 Flyway 기동 실패 DB 재생성 | 없음

## [학습 노트] `ddl-auto: update`로 만든 기존 스키마와 Flyway 히스토리 부재 충돌

| 항목 | 내용 |
| --- | --- |
| **날짜** | 2026-07-28 |
| **Issue** | 없음 |
| **PR** | 없음 (로컬 전용, `.gitignore`된 `application-local.yml` 수정) |
| **담당** | 표지민 (인프라) |
| **한 줄 요약** | Flyway 도입 전 `ddl-auto: update`로 만든 로컬 스키마 때문에 서버 기동이 실패해, DB를 통째로 재생성하는 방식을 선택 |

### 왜 고민을 시작했는가 (트리거)
- OAuth2 수정을 로컬에서 검증하려고 서버를 띄웠는데 `Found non-empty schema(s) 'career_dungeon' but no schema history table` 에러로 기동 자체가 실패함.
- 고민 1: 기존 로컬 데이터를 보존하며 Flyway를 붙일지, 통째로 밀고 새로 시작할지
- 고민 2: 로컬 전용 데이터라 유실돼도 괜찮은지

### 무엇을 어떻게 고민했는가
- 고민 1: `flyway.baseline-on-migrate: true` + `baseline-version: 6`을 임시로 넣어보는 방법(기존 스키마를 "이미 V6까지 적용된 것"으로 간주하고 이어붙이는 방식)과, DB를 지우고 Flyway가 V1부터 처음부터 다 적용하게 하는 방법(Method A) 두 가지를 놓고 비교함.
- 고민 2: 로컬 개발 DB라 실제로 보존해야 할 데이터가 없다는 걸 확인하고, 더 단순하고 확실한 쪽을 선택.

### 어떤 결정을 내렸는가
- 결정 1: "방법 A"로 DB를 완전히 재생성(`DROP DATABASE` → `CREATE DATABASE`) — baseline 설정은 되돌림(제거).
- 결정 2: `application-local.yml`의 `ddl-auto: update` → `none`으로 변경(ADR-010 준수) — 이제부터 Hibernate가 아니라 Flyway만 스키마를 관리.
- 결정 3: 이 파일이 `.gitignore` 대상이라 다른 팀원에게는 자동으로 전달 안 됨을 확인하고, 디스코드로 별도 공지가 필요하다고 판단.

### 고민 요약 (복습용 표)
| # | 왜 시작 | 어떻게 고민 | 최종 결정 |
| --- | --- | --- | --- |
| 1 | 서버 기동 자체가 실패 | baseline-on-migrate vs DB 재생성 비교 | 로컬이라 데이터 보존 불필요 → 재생성 선택 |
| 2 | `ddl-auto`와 Flyway 충돌 재발 우려 | ADR-010 기준 재확인 | `ddl-auto: none`으로 전환 |
| 3 | 다른 팀원도 같은 문제 겪을 가능성 | `.gitignore` 대상 파일임을 확인 | 디스코드 공지 필요로 판단 |

### 내가 이해한 흐름 (그림 말로)
1. 팀원이 로컬에 `ddl-auto: update`로 이미 테이블을 만들어놓은 상태
2. Flyway가 도입되면서 이 스키마는 있지만 자기가 관리한 이력(`flyway_schema_history`)이 없어서 기동을 거부
3. DB를 통째로 지우고 재생성(방법 A)
4. `ddl-auto: none`으로 바꿔서 이제부터는 Flyway만 스키마를 관리
5. 서버 재기동 → Flyway가 V1부터 순서대로 전부 적용 → 정상 기동

### 주의 (팀 규칙·실수 방지)
- `ddl-auto: update`와 Flyway를 동시에 켜두면, Flyway가 "내가 안 만든 테이블"을 만나서 기동을 거부한다 — 둘 중 하나만 스키마를 관리해야 한다.
- 이 수정은 `.gitignore`된 로컬 파일이라 커밋해도 팀원에게 전파되지 않는다 — 별도 공지가 필수다.
- 로컬이라도 `DROP DATABASE`류 명령은 실행 전 datasource URL이 로컬 인스턴스인지 반드시 재확인한다.

### 이번 PR 용어 3분 (새 것만, 최대 3개)
| 용어 | 쉬운 말 | 우리 문서 |
| --- | --- | --- |
| baseline-on-migrate | "여기까지는 이미 적용된 걸로 치고 그다음부터 관리해줘"라고 Flyway에게 알려주는 옵션 | `docs/operations/flyway-migration-guide.md` |
| flyway_schema_history | Flyway가 "내가 어떤 마이그레이션을 적용했는지" 기록해두는 테이블 | `docs/operations/flyway-migration-guide.md` |
| ddl-auto | Hibernate가 엔티티를 보고 스스로 테이블을 만들거나 바꾸는 기능. Flyway와 같이 쓰면 충돌 | `docs/operations/flyway-migration-guide.md` §4 |

### 스스로 확인 질문 (가리고 답하기)
1. Flyway가 "non-empty schema but no schema history table" 에러를 내는 조건은?
2. `ddl-auto: update`와 Flyway를 동시에 켜두면 왜 위험한가?
3. 로컬 개발 환경에서 baseline-on-migrate 대신 DB 재생성을 택한 이유는?

### 24시간 뒤 한 줄 회고 (비워둠)
-

### 팀 ADR PR 필요?
- [ ] Yes — 이유:
- [x] No — 이유: 이미 `docs/operations/flyway-migration-guide.md` §4에 로컬 초기화 절차가 문서화돼 있고, 이번 건은 개인 로컬 환경 문제이지 구조적 대안 검토가 필요한 결정은 아님.
