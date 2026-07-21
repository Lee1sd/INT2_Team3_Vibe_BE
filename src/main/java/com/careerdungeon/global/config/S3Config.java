package com.careerdungeon.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 여러 도메인이 공유할 수 있는 S3 인프라 빈. 자격증명은 AWS SDK 기본 자격증명 체인
 * (환경변수 → 프로파일 → 배포 환경에서는 IAM Role)을 그대로 쓴다 — 배포 환경에서는
 * IAM Role을, 로컬은 {@code .env}의 AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY를 우선
 * 사용한다(ADR-018). 이 클래스는 인프라(클라이언트 생성)만 담당하고, 프로필 이미지 등
 * 유스케이스별 버킷/키 정책은 각 도메인(auth 등)에서 구현한다.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${aws.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${aws.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
