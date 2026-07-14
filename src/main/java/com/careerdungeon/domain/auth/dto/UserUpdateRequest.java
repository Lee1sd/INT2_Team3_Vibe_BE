package com.careerdungeon.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UserUpdateRequest(@NotBlank(message = "이름은 비어 있을 수 없습니다.") String name) {}
