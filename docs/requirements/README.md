# requirements/

## CSV 원본 기반 (SSOT, `WBS_Vibe v5.2 - *.csv`)

| 문서 | 내용 |
| --- | --- |
| [`functional-requirements.md`](functional-requirements.md) | FR-01~FR-13, NFR-01~NFR-14, CM-001~003 |
| [`mvp-scope.md`](mvp-scope.md) | FEAT-00~FEAT-21 기능명세서, MVP/스트레치골 구분 |
| [`work-order.md`](work-order.md) | Phase 1~5 작업 순서도 (선행작업 의존성 포함) |
| [`wbs.md`](wbs.md) | 담당자별(①~⑥) 세부 작업 항목과 중요도 (v5.2: ② 작업 순서 1/2순위 명시) |

원본은 `프로그래머스_인턴쉽` 폴더의 `WBS_Vibe v5.2 - *.csv` 6개입니다(v5.1 → v5.2 갱신
시 요구사항명세서/MVP/작업순서도/api명세서는 내용 변경 없음, WBS·엔티티정의서만 실제
변경 있었음 — 상세는 각 문서 상단 안내 참고). 원본이 갱신되면 이 문서들도 같이 갱신하고,
갱신 근거를 `docs/adr/ADR-002-ai-agent-harness-engineering.md`의 "결정 이력" 절이나 그 주
회고에 한 줄 남기세요.

## 기획서(PDF) v5.1 기반

| 문서 | 내용 |
| --- | --- |
| [`planning-overview.md`](planning-overview.md) | 배경·타겟·페르소나/뱃지 설계 원칙·팀 구조·기술 스택·시스템 아키텍처 개요 |
| [`milestones.md`](milestones.md) | 4주 마일스톤 (도메인 × 주차) |
| [`test-strategy.md`](test-strategy.md) | 단위/통합/LLM 품질 테스트 전략, Mock 활용 전략 |
| [`security-design.md`](security-design.md) | 인증 보안 정책 값(토큰 만료 등), 파일/API 키/데이터 보안 |
| [`privacy-policy.md`](privacy-policy.md) | 개인정보 수집·처리·파기 정책 (내부 설계 기준) |
| [`open-questions.md`](open-questions.md) | 전체 문서에 흩어진 "팀 확인 필요" 항목 색인 |

원본은 `프로그래머스_인턴쉽` 폴더의
`[기획서] 커리어 던전_ AI 면접관 성장 시뮬레이터 v5.1.pdf`입니다.
