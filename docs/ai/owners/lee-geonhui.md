---
owner: lee-geonhui
domain: "① 파일 파이프라인"
paths:
  - "src/main/java/com/careerdungeon/domain/resume/**"
team: CareerDungeon_Backend
---

# Owner — 이건희 (파일 파이프라인 · 이력서)

> ⚠️ 이 파일은 "AI 면접관 페르소나"와 무관합니다. 이 파일은 **코드 담당자(이건희)의
> 작업 규칙**입니다. 면접관 설정은 `domain.persona`, `docs/erd/entity-definition.md`의
> `PersonaConfig`를 참고하세요.

## 역할 한 줄

이력서(PDF/TXT/MD) 업로드, PDFBox 기반 텍스트 추출, S3 저장/삭제, 추출 텍스트 캐싱,
데이터 풀(Resume/Portfolio) 관리, 활성 이력서(ACTIVE) 전환 로직.

## 수정 가능 경로

```
src/main/java/com/careerdungeon/domain/resume/**
```

## 손대지 말 것

- `domain/interview/**`, `domain/message/**` — LLM에 이력서 텍스트를 주입하는 시점부터는
  김한비(②)의 책임입니다. `resume` 도메인은 "추출된 텍스트를 어떤 형태로 제공하는가"까지만
  책임지고, 그 텍스트로 무엇을 하는지는 관여하지 않습니다.
- `global/security/**` — 업로드 API의 인증/인가 정책 자체를 바꿔야 한다면 표지민(④)에게
  먼저 알리세요. `resume` 컨트롤러에서 `@PreAuthorize` 등을 사용하는 것은 허용되지만,
  전역 보안 설정 변경은 표지민 소유입니다.

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/api/api-spec.md` | `RS-001`(업로드), `RS-002`(파싱 상태 폴링) |
| `docs/requirements/functional-requirements.md` | FR-01(업로드·추출), FR-11(PII 마스킹), NFR-01~03, NFR-14 |
| `docs/erd/entity-definition.md` | `Resume` 엔티티 (`type`, `s3Key`, `extractedText`, `parseStatus`, `fileHash`, `cacheExpiresAt`) |
| `docs/api/api-contract.md` | 에러 응답 포맷 확정 여부 (표지민 DRAFT) |
| `docs/requirements/privacy-policy.md` | 원본 삭제·캐시 파기·PII 마스킹 정책 (14장) |
| `docs/state/invariants-and-state-machines.md` §1 | `parseStatus` 상태 전이도 |

## ⚠️ 확인이 필요한 SSOT 불일치

마이페이지 목업 화면(기획 스크린샷)에는 "이력서 데이터 풀 (Resume) 필수 (1/3)",
"포트폴리오 데이터 풀 (Portfolio) (0/3)"처럼 **각각 최대 3개**까지 업로드하는 UI가
그려져 있습니다. 하지만 `functional-requirements.md`의 FR-01과 `entity-definition.md`는
**RESUME 정확히 1개(필수), PORTFOLIO 최대 1개(선택)**로 명시합니다. 이 둘이 서로
다릅니다. 구현 전에 팀에 확인하고, 확정되면 이 문서와 `docs/requirements/functional-requirements.md`를
함께 갱신하세요. 확정 전까지는 **FR-01(1개/1개)을 기준**으로 구현합니다 — 최신
요구사항명세서가 화면 목업보다 우선합니다. (색인: `docs/requirements/open-questions.md` #1)

## 체크리스트 (이건희)

- [ ] 업로드 파일 크기 제한(10MB, `application.yml` multipart 설정)과 API 명세서의 에러
      응답이 일치하는가? (NFR-01)
- [ ] type별 개수 제약 — RESUME 정확히 1개(필수), PORTFOLIO 최대 1개(선택)를 서버에서
      강제하는가? (위 SSOT 불일치 항목 확인 전까지는 이 기준으로 구현)
- [ ] PDF 파싱 실패(암호화된 PDF, 손상된 파일 등)를 명확한 에러 응답으로 처리하는가 —
      500으로 뭉개지지 않고 `parseStatus=FAILED`로 저장되는가? (FR-01)
- [ ] 원본 파일은 파싱 후 즉시 파기하는가(try-finally 보장)? (`docs/config` 화면의
      "원본 파일 즉시 파기" 문구와 실제 구현이 일치해야 함)
- [ ] 이력서/포트폴리오 재업로드 시 동일 type UPSERT(S3 객체 교체 + DB 레코드 갱신)가
      중복 누적 없이 처리되는가? (역방향 추적 ① DB 제약)
- [ ] 텍스트 캐싱 키(사용자ID+파일해시)와 TTL 30일이 구현되어 있는가? (NFR-03, 중요도 "중" — 없어도 서비스는 정상 동작하므로 다른 항목보다 후순위 가능)
- [ ] `cacheExpiresAt`(업로드 후 30일) 경과 레코드를 지우는 배치가 실제로 동작하는지
      확인했는가? (NFR-14)
- [ ] 이메일 등 PII 마스킹이 저장 전에 적용되고, 원본 텍스트는 저장하지 않는가? (FR-11, NFR-13 — 우선순위 "하", 다른 항목 완료 후 고려)
