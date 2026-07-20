package com.careerdungeon.domain.interview.service;

import com.careerdungeon.domain.auth.entity.User;
import com.careerdungeon.domain.persona.PersonaConfig;
import com.careerdungeon.domain.persona.PersonaConfigRepository;
import com.careerdungeon.domain.persona.PersonaTone;
import com.careerdungeon.domain.progress.entity.UserUnlockStatus;
import com.careerdungeon.domain.progress.repository.UserUnlockStatusRepository;
import com.careerdungeon.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewerServiceTest {

    @Mock
    PersonaConfigRepository personaConfigRepository;

    @Mock
    UserUnlockStatusRepository userUnlockStatusRepository;

    @Test
    @DisplayName("표시 이름 매핑이 없는 level/tone 조합은 BusinessException으로 거부한다")
    void missingDisplayNameMappingThrowsBusinessException() {
        InterviewerService sut = new InterviewerService(personaConfigRepository, userUnlockStatusRepository);
        long userId = 1L;
        UserUnlockStatus unlockStatus = unlockStatus(userId);
        when(userUnlockStatusRepository.findById(userId)).thenReturn(Optional.of(unlockStatus));
        when(personaConfigRepository.findAll()).thenReturn(List.of(new PersonaConfig(2, PersonaTone.LENIENT)));

        assertThatThrownBy(() -> sut.listInterviewers(userId))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INTERVIEWER_DISPLAY_NAME_NOT_FOUND");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }

    private UserUnlockStatus unlockStatus(long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        return UserUnlockStatus.initialFor(user);
    }
}
