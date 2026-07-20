package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.interview.dto.InterviewerListResponse;
import com.careerdungeon.domain.interview.dto.InterviewerResponse;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.global.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InterviewerService {

    private static final Map<DisplayNameKey, String> DISPLAY_NAMES = Map.of(
            new DisplayNameKey(1, PersonaTone.LENIENT), "널널한 대리",
            new DisplayNameKey(2, PersonaTone.STRICT), "깐깐한 과장");
    private static final long PRESSURE_PLACEHOLDER_ID = -3L;
    private static final int PRESSURE_PLACEHOLDER_LEVEL = 3;
    private static final String PRESSURE_PLACEHOLDER_TONE = "pressure";
    private static final String PRESSURE_PLACEHOLDER_NAME = "압박 부장";

    private final PersonaConfigRepository personaConfigRepository;
    private final UserUnlockStatusRepository userUnlockStatusRepository;

    public InterviewerService(
            PersonaConfigRepository personaConfigRepository,
            UserUnlockStatusRepository userUnlockStatusRepository) {
        this.personaConfigRepository = personaConfigRepository;
        this.userUnlockStatusRepository = userUnlockStatusRepository;
    }

    @Transactional(readOnly = true)
    public InterviewerListResponse listInterviewers(Long userId) {
        UserUnlockStatus unlockStatus = userUnlockStatusRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_UNLOCK_STATUS_NOT_FOUND",
                        "사용자 진행도 상태를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND));

        List<InterviewerResponse> interviewers = personaConfigRepository.findAll().stream()
                .filter(persona -> persona.getLevel() <= 2)
                .sorted(Comparator.comparingInt(PersonaConfig::getLevel))
                .map(persona -> toResponse(persona, unlockStatus.getUnlockedLevel()))
                .collect(Collectors.toCollection(ArrayList::new));
        interviewers.add(pressurePlaceholder());
        return new InterviewerListResponse(List.copyOf(interviewers));
    }

    private InterviewerResponse toResponse(PersonaConfig persona, int unlockedLevel) {
        return new InterviewerResponse(
                persona.getId(),
                displayName(persona),
                persona.getLevel(),
                persona.getTone().apiValue(),
                persona.getLevel() <= unlockedLevel,
                false);
    }

    private String displayName(PersonaConfig persona) {
        String displayName = DISPLAY_NAMES.get(new DisplayNameKey(persona.getLevel(), persona.getTone()));
        if (displayName == null) {
            throw new BusinessException(
                    "INTERVIEWER_DISPLAY_NAME_NOT_FOUND",
                    "면접관 표시 이름 매핑을 찾을 수 없습니다.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return displayName;
    }

    private InterviewerResponse pressurePlaceholder() {
        return new InterviewerResponse(
                PRESSURE_PLACEHOLDER_ID,
                PRESSURE_PLACEHOLDER_NAME,
                PRESSURE_PLACEHOLDER_LEVEL,
                PRESSURE_PLACEHOLDER_TONE,
                false,
                true);
    }

    private record DisplayNameKey(int level, PersonaTone tone) {
    }
}
