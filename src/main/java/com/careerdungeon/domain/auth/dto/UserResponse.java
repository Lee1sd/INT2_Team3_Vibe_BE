package com.careerdungeon.domain.auth.dto;

import com.careerdungeon.domain.auth.entity.User;

public record UserResponse(Long id, String name, String email, String photoUrl) {

    // photoUrl은 Presigned GET URL(TTL 10분, ADR-020)이라 매 요청 새로 생성해야 하므로
    // User 엔티티만으로는 만들 수 없다 — 호출자(UserService)가 생성해서 넘겨준다.
    public static UserResponse from(User user, String photoUrl) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), photoUrl);
    }
}
