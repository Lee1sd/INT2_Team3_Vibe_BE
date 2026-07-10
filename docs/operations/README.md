# operations/

LLM 및 외부 연동 관련 운영 정책을 정리하는 폴더입니다. Career Dungeon의 최대 리스크
(LLM 응답 불안정성, 3주 일정)에 대응하는 문서입니다.

| 문서 | 내용 |
| --- | --- |
| [`llm-cost-policy.md`](llm-cost-policy.md) | Mock 모드 기본 원칙, 예산 상한, 세부 모델 선택(⚠️ TBD), 호출 횟수 가드 |
| [`failure-policy.md`](failure-policy.md) | 파일/LLM/인증 실패 처리, 일정·배포 리스크 대응 |

담당: 김한비(LLM 호출 관련), 표지민(외부 연동 인프라 관련)이 함께 갱신. 이 문서는
`docs/ai/SHARED.md` §3의 역방향 추적 ⑤⑥ 항목의 판단 기준입니다.
