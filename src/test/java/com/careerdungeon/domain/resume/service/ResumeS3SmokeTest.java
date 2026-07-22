package com.careerdungeon.domain.resume.service;

import com.careerdungeon.global.config.S3Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "runS3SmokeTest", matches = "true")
class ResumeS3SmokeTest {

    @Test
    void putHeadGetAndDeleteTemporaryObjectOnRealS3() {
        String region = requiredEnvironmentVariable("AWS_REGION");
        String bucket = requiredEnvironmentVariable("AWS_S3_BUCKET");
        String key = "resumes/smoke-tests/" + UUID.randomUUID() + ".txt";
        byte[] expected = "career-dungeon-resume-s3-smoke-test".getBytes(StandardCharsets.UTF_8);

        try (S3Client s3Client = new S3Config().s3Client(region)) {
            ResumeFileStorage storage = new ResumeFileStorage(s3Client, null, bucket, 300L);
            boolean objectDeleted = false;
            try {
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType("text/plain")
                                .contentLength((long) expected.length)
                                .build(),
                        RequestBody.fromBytes(expected));
                StoredResumeFileMetadata metadata = storage.metadata(key);
                assertThat(metadata.contentLength()).isEqualTo(expected.length);
                assertThat(metadata.eTag()).isNotBlank();
                assertThat(storage.download(key, metadata.eTag())).isEqualTo(expected);

                storage.delete(key);
                objectDeleted = true;
            } finally {
                if (!objectDeleted) {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());
                }
            }
        }
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required for the S3 smoke test.");
        }
        return value;
    }
}
