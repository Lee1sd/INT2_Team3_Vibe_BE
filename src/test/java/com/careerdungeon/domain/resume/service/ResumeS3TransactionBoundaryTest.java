package com.careerdungeon.domain.resume.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeS3TransactionBoundaryTest {

    @Test
    void s3NetworkOrchestratorsDoNotOpenTransactions() throws Exception {
        assertThat(AnnotationUtils.findAnnotation(
                ResumeService.class.getMethod("completeUpload", Long.class,
                        com.careerdungeon.domain.resume.dto.ResumeUploadCompleteRequest.class),
                Transactional.class)).isNull();
        assertThat(AnnotationUtils.findAnnotation(
                ResumeParsingService.class.getMethod("handleResumeUploaded",
                        com.careerdungeon.domain.resume.event.ResumeUploadedEvent.class),
                Transactional.class)).isNull();
        assertThat(AnnotationUtils.findAnnotation(
                ResumeFileCleanupService.class.getMethod("retryPendingTasks"),
                Transactional.class)).isNull();
    }

    @Test
    void databaseOnlyParsingUpdatesUseShortTransactions() throws Exception {
        assertThat(AnnotationUtils.findAnnotation(
                ResumeParsingPersistenceService.class.getMethod(
                        "markDoneIfActive", Long.class, String.class, java.time.Instant.class),
                Transactional.class)).isNotNull();
        assertThat(AnnotationUtils.findAnnotation(
                ResumeParsingPersistenceService.class.getMethod("markFailedIfActive", Long.class),
                Transactional.class)).isNotNull();
    }
}
