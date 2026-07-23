package com.careerdungeon.domain.resume.service;

import com.careerdungeon.domain.resume.entity.ParseStatus;
import com.careerdungeon.domain.resume.entity.ResumeType;
import com.careerdungeon.domain.resume.exception.ResumeTypeLimitExceededException;
import com.careerdungeon.domain.resume.repository.ResumeRepository;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ResumeCapacityPolicy {

    private static final int MAX_RESUME_PER_TYPE = 3;
    private static final Set<ParseStatus> NON_SLOT_STATUSES =
            Set.of(ParseStatus.FAILED, ParseStatus.EXPIRED);

    private final ResumeRepository resumeRepository;

    public ResumeCapacityPolicy(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    public void ensureAvailable(Long userId, ResumeType type) {
        long count = resumeRepository.countByUserIdAndTypeAndParseStatusNotInAndDeletedAtIsNull(
                userId, type, NON_SLOT_STATUSES);
        if (count >= MAX_RESUME_PER_TYPE) {
            throw new ResumeTypeLimitExceededException(type);
        }
    }

    public void ensureAvailableWithUserLock(Long userId, ResumeType type) {
        if (resumeRepository.lockUserForResumeCapacity(userId).isEmpty()) {
            throw new IllegalStateException("Authenticated user does not exist.");
        }
        ensureAvailable(userId, type);
    }
}
