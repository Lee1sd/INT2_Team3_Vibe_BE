package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.exception.ResumeObjectVersionMismatchException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfSystemProperty(named = "runS3SmokeTest", matches = "true")
class ResumeS3SmokeTest {

    @Test
    void putHeadGetAndDeleteTemporaryObjectOnRealS3() {
        String region = requiredEnvironmentVariable("AWS_REGION");
        String bucket = requiredEnvironmentVariable("AWS_S3_BUCKET");
        String key = "resumes/smoke-tests/" + UUID.randomUUID() + ".txt";
        byte[] original = "career-dungeon-resume-s3-smoke-test-original".getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "career-dungeon-resume-s3-smoke-test-replacement".getBytes(StandardCharsets.UTF_8);

        try (S3Client s3Client = new S3Config().s3Client(region)) {
            ResumeFileStorage storage = new ResumeFileStorage(s3Client, null, bucket, 300L);
            boolean objectDeleted = false;
            try {
                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType("text/plain")
                                .contentLength((long) original.length)
                                .build(),
                        RequestBody.fromBytes(original));
                StoredResumeFileMetadata originalMetadata = storage.metadata(key);
                assertThat(originalMetadata.contentLength()).isEqualTo(original.length);
                assertThat(originalMetadata.eTag()).isNotBlank();
                assertThat(storage.download(key, originalMetadata.eTag())).isEqualTo(original);

                storage.delete(key, originalMetadata.eTag());

                s3Client.putObject(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentType("text/plain")
                                .contentLength((long) replacement.length)
                                .build(),
                        RequestBody.fromBytes(replacement));
                StoredResumeFileMetadata replacementMetadata = storage.metadata(key);
                assertThat(replacementMetadata.eTag()).isNotEqualTo(originalMetadata.eTag());

                assertThatThrownBy(() -> storage.delete(key, originalMetadata.eTag()))
                        .isInstanceOf(ResumeObjectVersionMismatchException.class);
                assertThat(storage.download(key, replacementMetadata.eTag())).isEqualTo(replacement);

                storage.delete(key, replacementMetadata.eTag());
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
