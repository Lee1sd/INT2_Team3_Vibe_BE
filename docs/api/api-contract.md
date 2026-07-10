# api-contract.md — 공통 응답 포맷 · 에러 코드 (CM-002)

> 이 문서는 `docs/ai/owners/pyo-jimin.md`의 위임 규칙에 따라 표지민이 확정합니다.
> 확정되면 이건희/김한비/최용성은 이 문서만 보고 각자 컨트롤러/예외를 구현합니다.

## 상태: ✅ CONFIRMED (2026-07-10, 표지민 확정)

## 성공 응답

**래퍼를 씌우지 않는다.** `docs/api/api-spec.md`에 있는 각 엔드포인트의 Response 예시를
그대로 최상위 바디로 반환한다 (예: `{"resumeId": 501, "type": "RESUME", "parseStatus": "PROCESSING"}`).
`{"data": ..., "success": true}` 같은 공통 래퍼는 사용하지 않는다 — MVP 3주 범위에서
불필요한 보일러플레이트로 판단.

## 에러 응답 (확정)

```json
{
  "code": "RESUME_TYPE_LIMIT_EXCEEDED",
  "message": "이력서(RESUME)는 최대 3개까지만 업로드할 수 있습니다.",
  "status": 400
}
```

- `code`: 도메인별 스크리밍 스네이크 케이스 에러 코드. 접두사로 도메인을 구분
  (예: `RESUME_*`, `AUTH_*`, `INTERVIEW_*`, `JUDGMENT_*`).
- `message`: 사용자에게 그대로 노출 가능한 한국어 문장.
- `status`: HTTP 상태 코드와 동일한 값을 바디에도 중복 포함(클라이언트 편의).
- `fieldErrors[]`(필드별 검증 오류 배열)는 **추가하지 않는다** — 이 프로젝트는 대부분
  멀티파트 파일 업로드/짧은 JSON이라 필드 여러 개가 동시에 잘못될 일이 적음. 나중에
  실제로 필요해지면 그때 이 문서에 변경 제안을 추가한다(위임 규칙 §2).

## 구현 방법 (Spring `@RestControllerAdvice`)

`global/exception/` 패키지에 아래 3개 클래스로 구현한다. 다른 도메인(①②③)은 이 중
`BusinessException`만 알면 되고, 나머지는 표지민이 만든 뒤로는 신경 쓸 필요가 없다.

```java
// global/exception/BusinessException.java
public class BusinessException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
```

```java
// global/exception/ErrorResponse.java
public record ErrorResponse(String code, String message, int status) {}
```

```java
// global/exception/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus())
            .body(new ErrorResponse(e.getCode(), e.getMessage(), e.getStatus().value()));
    }

    @ExceptionHandler(Exception.class) // 예상 못한 예외의 최종 방어선
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        return ResponseEntity.status(500)
            .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다.", 500));
    }
}
```

다른 도메인 코드에서는 이렇게 던지기만 하면 된다:

```java
throw new BusinessException("RESUME_TYPE_LIMIT_EXCEEDED", "이력서는 최대 3개까지만 업로드할 수 있습니다.", HttpStatus.BAD_REQUEST);
```

## 알고 있는 예외 케이스 (요구사항명세서 근거)

| 상황 | 근거 |
| --- | --- |
| 확장자·용량 위반 | FR-01, NFR-01 → 400 |
| RESUME 미존재 상태로 세션 생성 시도 | FR-01 → 세션 생성 차단 |
| PDFBox 추출 실패 | FR-01 → `parse_status=FAILED` 저장 후 재업로드 안내 |
| 인증 실패 | FR-06 → 401 |
| Refresh Token 만료 | FR-06 → 재로그인 유도 |
| LLM 응답 스키마 오류 | NFR-05 → 최대 2회 재요청, 3회째 실패 처리 |
| 동점 처리 | FR-04 → 랜덤 선택 (에러 아님, 참고용) |

## 위임 규칙 재확인

1. 이 문서는 확정되었습니다(`✅ CONFIRMED`, 2026-07-10). 이건희/김한비/최용성은 이제
   표지민에게 매번 물어보지 않고 이 문서 + `BusinessException`만 보고 각자 도메인의
   예외를 구현하면 됩니다.
2. 확정 후 계약을 바꿔야 하는 상황이 생기면, 변경을 요청하는 쪽이 먼저 이 문서에
   변경 제안을 추가하고 표지민의 승인을 받은 뒤 반영합니다.
3. 표지민이 `global/exception/BusinessException.java`,
   `global/exception/ErrorResponse.java`, `global/exception/GlobalExceptionHandler.java`를
   먼저 만들어야 다른 세 도메인이 실제로 예외를 던질 수 있습니다 — 이 세 파일이 표지민의
   1주차 최우선 산출물입니다.
