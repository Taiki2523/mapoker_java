package com.mapoker.domain.rules;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ベッティングストリートを表す列挙型です。
 */
public enum Street {
    /** プリフロップです。 */
    PREFLOP("preflop"),
    /** フロップです。 */
    FLOP("flop"),
    /** ターンです。 */
    TURN("turn"),
    /** リバーです。 */
    RIVER("river");

    private final String label;

    Street(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /**
     * 次のストリートを返します。
     *
     * @return 次のストリート
     */
    public Street next() {
        return values()[ordinal() + 1];
    }
}
