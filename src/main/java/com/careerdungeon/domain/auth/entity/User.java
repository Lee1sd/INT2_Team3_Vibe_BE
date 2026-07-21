package com.careerdungeon.domain.auth.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String googleId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    // S3 object key만 저장한다(URL 아님) — ADR-018. presigned GET URL은 매 요청 새로 생성한다.
    @Column(name = "profile_image_key")
    private String profileImageKey;

    protected User() {}

    public User(String googleId, String email, String name) {
        this.googleId = googleId;
        this.email = email;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getGoogleId() { return googleId; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getProfileImageKey() { return profileImageKey; }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateProfileImageKey(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User u)) return false;
        return googleId != null && googleId.equals(u.googleId);
    }

    @Override
    public int hashCode() {
        return googleId == null ? 0 : googleId.hashCode();
    }
}
