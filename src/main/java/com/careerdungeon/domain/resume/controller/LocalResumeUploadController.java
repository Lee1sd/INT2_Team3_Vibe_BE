package com.careerdungeon.domain.resume.controller;

import com.careerdungeon.domain.resume.service.LocalResumeFileStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 저장 모드에서만 활성화되는 원본 파일 PUT 엔드포인트다.
 * 전역 보안 정책을 그대로 적용하며 프런트가 JWT를 함께 보내야 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/resumes/local-upload")
@ConditionalOnProperty(name = "resume.storage.mode", havingValue = "local")
public class LocalResumeUploadController {
    private final LocalResumeFileStorage localResumeFileStorage;

    /** 로컬 프로필에서 활성화된 임시 저장소를 연결한다. */
    public LocalResumeUploadController(LocalResumeFileStorage localResumeFileStorage) {
        this.localResumeFileStorage = localResumeFileStorage;
    }

    /**
     * 발급된 토큰·사용자·헤더·실제 바이트 크기를 검증하고 응답 본문 없이 완료한다.
     */
    @PutMapping("/{token}")
    public ResponseEntity<Void> upload(
            @AuthenticationPrincipal Long userId,
            @PathVariable String token,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestBody byte[] bytes) {
        localResumeFileStorage.upload(userId, token, contentType, bytes);
        return ResponseEntity.noContent().build();
    }
}
