package com.careerdungeon.domain.persona;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * level UNIQUE 제약(코드래빗 지적 P2)이 DB 레벨에서 실제로 강제되는지 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
class PersonaConfigRepositoryTest {

    @Autowired
    private PersonaConfigRepository sut;

    @Test
    @DisplayName("같은 level로 두 번째 PersonaConfig 저장 시 UNIQUE 제약 위반")
    void duplicateLevel_violatesUniqueConstraint() {
        sut.saveAndFlush(new PersonaConfig(1, PersonaTone.LENIENT));

        assertThatThrownBy(() -> sut.saveAndFlush(new PersonaConfig(1, PersonaTone.STRICT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
