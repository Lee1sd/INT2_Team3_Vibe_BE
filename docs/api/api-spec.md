# api-spec.md — API 명세서

> 원본: `WBS_Vibe v5.2 - api 명세서.csv` (v5.2에서 내용 변경 없음). 표 형식만 마크다운으로 바꿨고 내용은 그대로
> 옮겼습니다. 공통 응답 포맷/에러 코드는 `docs/ai/owners/pyo-jimin.md`의 위임 규칙에 따라
> 표지민이 확정하는 `api-contract.md`를 따로 둡니다.

## 인증 (Auth) — Google OAuth2 + JWT

### AU-001 — GET `/api/auth/oauth2/google`

- 설명: 구글 소셜 로그인 시작
- 인증 필요: No
- 상태 코드: 302
- 비고: 커스텀 경로 사용 (Google 로그인 화면으로 302 리다이렉트)

### AU-002 — GET `/api/auth/oauth2/callback`

- 설명: 구글 로그인 콜백 처리 → JWT 발급
- Request: `code`, `state` (Google 자동 전달)
- Response 예시:

```json
{
  "accessToken": "eyJhbGciOi...",
  "user": {
    "id": 1,
    "name": "이건희",
    "email": "lee@example.com"
  }
}
```

- 인증 필요: No / 상태 코드: 200
- 비고: Refresh Token은 HttpOnly/Secure/Strict 쿠키로 별도 발급. Access Token 30분 만료

### AU-003 — POST `/api/auth/refresh`

- 설명: Access Token 재발급
- Request: (Refresh Token 쿠키 자동 전송)
- Response 예시:

```json
{
  "accessToken": "eyJhbGciOi..."
}
```

- 인증 필요: Cookie / 상태 코드: 200/401
- 비고: 자동 재발급 + 로테이션

### AU-004 — POST `/api/auth/logout`

- 설명: 로그아웃
- Response 예시:

```json
{
  "message": "로그아웃 되었습니다"
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 쿠키 삭제 + 서버측 Refresh Token 무효화

## 유저 프로필 (User Profile) — 7/9 신규(개인화)

### UP-001 — PATCH `/api/users/me`

- 설명: 마이페이지에서 이름 수정
- Request: `{"name": "홍길동"}`
- Response 예시:

```json
{
  "id": 1,
  "name": "홍길동"
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 수정된 이름은 이후 질문/피드백 생성 시 프롬프트에 반영됨(예: "OO님, ...")

## 이력서/포트폴리오 (Resume)

### RS-001 — POST `/api/resumes`

- 설명: 이력서/포트폴리오 업로드 및 텍스트 추출 (type 파라미터로 구분)
- Request:

```json
{
  "type": "RESUME",
  "file": "(multipart)"
}
```

- Response 예시:

```json
{
  "resumeId": 501,
  "type": "RESUME",
  "parseStatus": "PROCESSING"
}
```

- 인증 필요: Yes / 상태 코드: 201/400
- 비고: `type=RESUME`(필수, 정확히 1개)/`PORTFOLIO`(선택, 정확히 1개) — 3장#1 확정. 동일
  type 재업로드 시 기존 파일 교체(UPSERT). 추출 시 이름/연락처/이메일 등 PII 마스킹
  처리(⚠️ 마스킹 대상·방식 팀 합의 필요)

### RS-002 — GET `/api/resumes/{resumeId}`

- 설명: 파싱 상태 조회 (폴링)
- Response 예시:

```json
{
  "resumeId": 501,
  "type": "RESUME",
  "parseStatus": "DONE",
  "extractedText": "(마스킹 처리됨, 생략)"
}
```

- 인증 필요: Yes / 상태 코드: 200/404
- 비고: `parseStatus`: PROCESSING/DONE/FAILED

## 키워드 (Keyword)

### KW-001 — GET `/api/keywords`

- 설명: 선택 가능한 키워드 목록 조회
- Response 예시:

```json
{
  "keywords": ["DB", "보안"]
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: MVP는 2개만 노출 권장(DB, 보안)

## 면접관 (Interviewer/PersonaConfig)

### IV-001 — GET `/api/interviewers`

- 설명: 면접관(페르소나) 리스트 및 해금 정보
- Response 예시:

```json
{
  "interviewers": [
    { "id": 1, "name": "널널한 대리", "level": 1, "tone": "lenient", "unlocked": true, "comingSoon": false },
    { "id": 2, "name": "깐깐한 과장", "level": 2, "tone": "strict", "unlocked": false, "comingSoon": false },
    { "id": 3, "name": "압박 페르소나", "level": 3, "tone": "pressure", "unlocked": false, "comingSoon": true }
  ]
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: MVP는 Lv.1/Lv.2만 실제 기능 동작. Lv.3은 `comingSoon=true`로 UI에 회색/잠금
  표시만 하고 실제 면접 진행 로직은 없음(프론트 전용 placeholder)

## 뱃지 (Badge)

### BG-001 — GET `/api/badges/me`

- 설명: 내가 획득한 뱃지 목록 조회
- Response 예시:

```json
{
  "badges": [
    {
      "badgeId": 1,
      "stage": 1,
      "name": "프로그램 머쓱(초안)",
      "imageUrl": "...",
      "acquiredAt": "2026-07-08T10:00:00"
    }
  ]
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 4단계 확정 — Stage1=가입직후 / Stage2=Lv.1해금 / Stage3=Lv.2해금 /
  Stage4=Lv.3해금(면접만렙). ⚠️ Stage3/4 매핑은 원문(Lv.3/Lv.4 표기) 대비 한 칸 당겨
  해석한 것 — 팀 확인 필요

## 채팅 히스토리 (History)

### HS-001 — GET `/api/interviews/history`

- 설명: 레벨별 채팅 히스토리 사이드바 조회
- Response 예시:

```json
{
  "levels": [
    {
      "level": 1,
      "sessions": [
        { "sessionId": 9001, "createdAt": "2026-07-08T10:00:00", "totalScore": 67 }
      ]
    }
  ]
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 레벨별 폴더 구조로 세션 목록 반환. 기존 `InterviewSession`+`Message`로 충분

## 유저 진행도 (User Progress)

### UM-001 — GET `/api/users/me/progress`

- 설명: 메인페이지 전체 진행도 게이지 조회
- Response 예시:

```json
{
  "unlockedLevel": 2,
  "progressGauge": 30
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: ⚠️ 레벨 클리어마다 +30%씩 누적되는 계정 전체 진행도. 세션별 판정점수(0~100)와는
  별개 개념. 3레벨×30%=90%인지 100%로 맞출지 등 구체 비율 팀 확인 필요 → 30, 30, 40
  채워서 100으로 맞출 예정

## 면접 세션 (Interview Session)

### IS-001 — POST `/api/interviews`

- 설명: 면접 세션 생성 + 질문 3개 일괄 생성 (keyword 포함)
- Request:

```json
{
  "resumeId": 501,
  "interviewerId": 1,
  "keyword": "DB"
}
```

- Response 예시:

```json
{
  "sessionId": 9001,
  "status": "IN_PROGRESS",
  "questions": [
    { "questionId": 1, "question": "이 프로젝트에서 캐싱 전략을 선택한 이유는?" },
    { "questionId": 2, "question": "동시성 문제는 어떻게 처리했나요?" },
    { "questionId": 3, "question": "장애 발생 시 복구 전략은?" }
  ]
}
```

- 인증 필요: Yes / 상태 코드: 201
- 비고: `resumeId`는 반드시 `type=RESUME`만 허용. 질문 생성 시 참고 질문 예시(few-shot)를
  프롬프트에 포함해 실무형 품질 강화. 질문/피드백에 사용자 이름 반영(예: "OO님, ...")

### IS-002 — POST `/api/interviews/{id}/answers`

- 설명: 답변 제출 (최초 3개 일괄 또는 꼬리질문 1개) — 상태에 따라 배치채점/최종판정 자동 분기
- Request(최초 3개 일괄):

```json
{
  "answers": [
    { "questionId": 1, "answerText": "..." },
    { "questionId": 2, "answerText": "..." },
    { "questionId": 3, "answerText": "..." }
  ]
}
```

- Response 예시:

```json
{
  "evaluations": [
    { "questionId": 1, "score": 20, "feedback": "..." },
    { "questionId": 2, "score": 25, "feedback": "..." },
    { "questionId": 3, "score": 15, "feedback": "부족한 부분: ..." }
  ],
  "totalScore": 60,
  "weakestQuestionId": 3,
  "passed": false,
  "nextTurn": {
    "type": "FOLLOW_UP",
    "targetQuestionId": 3,
    "question": "그 캐싱 전략에서 정합성 문제는 어떻게 처리하셨나요?"
  }
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: 채점 기준 5개 세부항목(7/9 확정)으로 내부 계산 — 기술적정확성10 / 핵심내용충족도5
  / 근거판단과정4 / 구체성실무연계3 / 트레이드오프예외3 = 25점. 단 API 응답·화면엔
  세부점수 노출 안 함, `score`+`feedback`(문장)만 제공. `totalScore`는 0~100 총점 그대로
  (% 환산 없음). 채점 호출 내에서 모범답변을 생성해 즉시 비교하는 방식

### IS-002b — POST `/api/interviews/{id}/answers` (2번째 호출 예시: 꼬리질문 답변 제출 → 최종 판정)

- Request:

```json
{
  "answers": [
    { "questionId": 4, "answerText": "..." }
  ]
}
```

- Response 예시:

```json
{
  "evaluations": [
    { "questionId": 1, "score": 20 },
    { "questionId": 2, "score": 25 },
    { "questionId": 4, "score": 22, "feedback": "..." }
  ],
  "totalScore": 67,
  "passed": false,
  "overallFeedback": "전반적으로 논리 전개는 탄탄했으나 세 번째 답변에서 트레이드오프 고려가 부족했습니다.",
  "nextTurn": null
}
```

- 인증 필요: Yes / 상태 코드: 200
- 비고: `tierLabel`/`tierDescription` 필드 제거(정정) — 등급 텍스트는 레벨 숫자 기준
  프론트 정적 표기라 API 응답에 불필요

## 공통 / 인프라

### CM-001 — LLM 인터페이스 내부

- LLM 벤더: Claude(Haiku 4.5/Sonnet 4.6) 확정. Mock 모드 시 고정 JSON 반환.
- 비고: LLM 벤더는 Claude 확정(Gemini/GPT 탈락). 세부 모델(Haiku 4.5 vs Sonnet 4.6)은
  미정 — 2주차 실연동 전 프롬프트 테스트 후 확정 예정

### CM-002 — 공통 예외처리/응답 포맷

- 비고: 공통코드 오너 = ④번(표지민) 확정. 인증+프론트+인프라 겸임 중이나, 프론트 작업이
  상당 부분 완료되어 업무량 감당 가능하다고 본인 확인함(7/10 기준)

### CM-003 — S3 버킷 정책

- S3 접근 제어: 퍼블릭 액세스 전체 차단 + IAM Role 기반 접근 제어 확정
