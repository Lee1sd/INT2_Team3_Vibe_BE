package com.careerdungeon.domain.persona;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonaConfigTest {

    @Test
    @DisplayName("level=1, tone=LENIENT → 정상 생성 (Lv.1 널널한 대리)")
    void level1_lenient_createsSuccessfully() {
        var config = new PersonaConfig(1, PersonaTone.LENIENT);

        assertThat(config.getLevel()).isEqualTo(1);
        assertThat(config.getTone()).isEqualTo(PersonaTone.LENIENT);
    }

    @Test
    @DisplayName("level=2, tone=STRICT → 정상 생성 (Lv.2 깐깐한 과장)")
    void level2_strict_createsSuccessfully() {
        var config = new PersonaConfig(2, PersonaTone.STRICT);

        assertThat(config.getLevel()).isEqualTo(2);
        assertThat(config.getTone()).isEqualTo(PersonaTone.STRICT);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 4})
    @DisplayName("level이 1~2 범위 밖(Lv.3 압박 페르소나 포함) → IllegalArgumentException")
    void level_outOfSupportedRange_throws(int invalidLevel) {
        assertThatThrownBy(() -> new PersonaConfig(invalidLevel, PersonaTone.LENIENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1~2");
    }

    @Test
    @DisplayName("tone=null → NullPointerException")
    void nullTone_throwsNpe() {
        assertThatThrownBy(() -> new PersonaConfig(1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tone");
    }

    @Test
    @DisplayName("level 1~2 범위 내에서는 예외 없이 생성된다")
    void level_withinSupportedRange_doesNotThrow() {
        assertThatCode(() -> new PersonaConfig(1, PersonaTone.LENIENT)).doesNotThrowAnyException();
        assertThatCode(() -> new PersonaConfig(2, PersonaTone.STRICT)).doesNotThrowAnyException();
    }
}
