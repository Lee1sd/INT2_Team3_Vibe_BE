package com.careerdungeon.global.exception;

import com.careerdungeon.domain.auth.repository.UserRepository;
import com.careerdungeon.global.exception.LlmPermanentFailureException;
import com.careerdungeon.global.llm.exception.LlmProviderConfigException;
import com.careerdungeon.global.llm.exception.LlmSchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final UserRepository userRepository;

    public GlobalExceptionHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage(), e.getStatus().value()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(LlmPermanentFailureException.class)
    public ResponseEntity<ErrorResponse> handleLlmPermanentFailure(LlmPermanentFailureException e) {
        LlmFailureLogContext context = LlmFailureLogContext.from(e);
        log.error(
                "LLM 영구 실패 (재시도 소진): correlationId={}, causeType={}, providerStatus={}",
                context.correlationId(),
                context.causeType(),
                context.providerStatus());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("LLM_FAILURE", "질문 생성/채점에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @ExceptionHandler(LlmProviderConfigException.class)
    public ResponseEntity<ErrorResponse> handleLlmProviderConfig(LlmProviderConfigException e) {
        LlmFailureLogContext context = LlmFailureLogContext.from(e);
        log.error(
                "LLM provider 설정/인증 실패: correlationId={}, causeType={}, providerStatus={}",
                context.correlationId(),
                context.causeType(),
                context.providerStatus());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("LLM_PROVIDER_CONFIG_ERROR", "LLM 설정을 확인해 주세요.", HttpStatus.SERVICE_UNAVAILABLE.value()));
    }

    @ExceptionHandler(LlmSchemaValidationException.class)
    public ResponseEntity<ErrorResponse> handleLlmSchema(LlmSchemaValidationException e) {
        LlmFailureLogContext context = LlmFailureLogContext.from(e);
        log.error(
                "LLM 스키마 검증 실패: correlationId={}, causeType={}, providerStatus={}",
                context.correlationId(),
                context.causeType(),
                context.providerStatus());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("LLM_SCHEMA_ERROR", "LLM 응답 형식이 올바르지 않습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.BAD_GATEWAY.value()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String value = e.getValue() != null ? String.valueOf(e.getValue()) : "(빈 값)";
        String message = String.format("파라미터 '%s'의 값이 올바르지 않습니다: %s", e.getName(), value);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_PARAMETER_TYPE", message, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        String message = String.format("필수 파라미터 '%s'가 누락되었습니다.", e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MISSING_REQUIRED_PARAMETER", message, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RESOURCE_NOT_FOUND", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN.value()));
    }

    /**
     * 이슈 #107 — 탈퇴한 유저의 accessToken은 서명만 유효하면 만료(최대 30분)까지
     * 통과하는데(JwtAuthenticationFilter는 서명만 검증, DB 조회 없음), 그 상태로 쓰기
     * API를 호출하면 user_id FK가 가리키는 users 행이 이미 없어 500(catch-all)으로
     * 응답되고 있었다. 이 핸들러는 DataIntegrityViolationException을 잡되, "인증된
     * userId가 실제로 DB에 존재하는가"를 확인해서 그 경우에만 401로 좁혀 바꾼다 — 다른
     * 원인의 FK/제약 위반(예: 존재하지 않는 리소스를 잘못 참조)까지 뭉뚱그려 401로
     * 바꾸지 않기 위함이다. 이 존재 확인 쿼리는 이 예외가 실제로 발생했을 때만
     * 실행되므로 매 요청마다 DB를 추가로 때리지 않는다(ADR-016에서 반려된
     * "요청마다 유저 존재 확인" 대안과는 다르다).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (isAuthenticatedUserDeleted()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(
                            "AUTHENTICATION_REQUIRED",
                            "인증된 사용자 계정을 찾을 수 없습니다. 다시 로그인해 주세요.",
                            HttpStatus.UNAUTHORIZED.value()));
        }
        log.error("예상치 못한 예외 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    private boolean isAuthenticatedUserDeleted() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            return false;
        }
        return !userRepository.existsById(userId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("예상치 못한 예외 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    record LlmFailureLogContext(String correlationId, String causeType, String providerStatus) {

        static LlmFailureLogContext from(Throwable throwable) {
            Throwable cause = deepestCause(throwable);
            return new LlmFailureLogContext(
                    UUID.randomUUID().toString(),
                    cause.getClass().getSimpleName(),
                    providerStatus(throwable));
        }

        private static Throwable deepestCause(Throwable throwable) {
            Throwable current = throwable;
            while (current.getCause() != null) {
                current = current.getCause();
            }
            return current;
        }

        private static String providerStatus(Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                if (current instanceof LlmProviderConfigException providerConfigException
                        && providerConfigException.statusCode() != null) {
                    return String.valueOf(providerConfigException.statusCode());
                }
                if (current instanceof LlmSchemaValidationException schemaValidationException
                        && schemaValidationException.statusCode() != null) {
                    return String.valueOf(schemaValidationException.statusCode());
                }
                current = current.getCause();
            }
            return "none";
        }
    }
}
