package com.careerdungeon.domain.auth.dto;

public record LoginResponse(String accessToken, UserInfo user) {
    public record UserInfo(Long id, String name, String email) {}
}
