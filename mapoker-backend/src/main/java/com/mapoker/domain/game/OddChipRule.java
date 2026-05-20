package com.mapoker.domain.game;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 端数チップの配分ルールを表す列挙型です。
 */
public enum OddChipRule {
    /** 席番号が小さい順に配分します。 */
    LOW_INDEX("low_index"),
    /** ボタン左隣から配分します。 */
    BUTTON_LEFT("button_left");

    private final String label;

    OddChipRule(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /**
     * ラベルから端数チップルールを解決します。
     *
     * @param label ルールラベル
     * @return 対応する端数チップルール
     * @throws IllegalArgumentException 未知のラベルが指定された場合
     */
    @JsonCreator
    public static OddChipRule fromLabel(String label) {
        for (OddChipRule r : values()) {
            if (r.label.equalsIgnoreCase(label)) return r;
        }
        throw new IllegalArgumentException("Unknown odd chip rule: " + label);
    }
}
