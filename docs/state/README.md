# state/

`InterviewSession.status`, `Resume.parseStatus`, 레벨업/뱃지 해금 등 상태 전이가 있는
도메인의 상태 다이어그램과 불변식(invariant)을 정리하는 폴더입니다.

| 문서 | 내용 |
| --- | --- |
| [`invariants-and-state-machines.md`](invariants-and-state-machines.md) | `parseStatus`/`InterviewSession.status` 전이도, 레벨 해금·뱃지 지급 불변식, `persona_config` 정의 |

담당: 김한비(면접 세션 상태), 최용성(게이지/레벨 상태)이 함께 갱신. 실제 코드에 상태값
(enum)이나 전이 로직을 추가/변경할 때는 이 문서를 먼저 갱신하고 커밋에 함께 포함하세요
(`docs/ai/SHARED.md` §3 ① DB 제약 역방향 추적).
