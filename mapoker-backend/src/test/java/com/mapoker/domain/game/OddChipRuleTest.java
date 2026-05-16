package com.mapoker.domain.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OddChipRule の単体テスト。
 */
class OddChipRuleTest {

    // -----------------------------------------------------------------------
    // getLabel
    // -----------------------------------------------------------------------

    @Test
    void getLabelLowIndex() {
        assertThat(OddChipRule.LOW_INDEX.getLabel()).isEqualTo("low_index");
    }

    @Test
    void getLabelButtonLeft() {
        assertThat(OddChipRule.BUTTON_LEFT.getLabel()).isEqualTo("button_left");
    }

    // -----------------------------------------------------------------------
    // fromLabel — 正常系
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
            "low_index,  LOW_INDEX",
            "LOW_INDEX,  LOW_INDEX",
            "Low_Index,  LOW_INDEX",
            "button_left, BUTTON_LEFT",
            "BUTTON_LEFT, BUTTON_LEFT",
    })
    void fromLabelIsCaseInsensitive(String input, OddChipRule expected) {
        assertThat(OddChipRule.fromLabel(input)).isEqualTo(expected);
    }

    // -----------------------------------------------------------------------
    // fromLabel — 異常系
    // -----------------------------------------------------------------------

    @Test
    void fromLabelThrowsForUnknownLabel() {
        assertThatThrownBy(() -> OddChipRule.fromLabel("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown odd chip rule: unknown");
    }
}
