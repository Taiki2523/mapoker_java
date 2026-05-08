package com.mapoker.domain.rules;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * プレイヤー行動の種類を表す列挙型です。
 */
public enum ActionType {
    /** フォールドです。 */
    FOLD("fold"),
    /** チェックです。 */
    CHECK("check"),
    /** コールです。 */
    CALL("call"),
    /** ベットです。 */
    BET("bet"),
    /** レイズです。 */
    RAISE("raise"),
    /** オールインです。 */
    ALL_IN("all_in");

    private final String label;

    ActionType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /**
     * ラベルから行動種別を解決します。
     *
     * @param label 行動ラベル
     * @return 対応する行動種別
     * @throws IllegalArgumentException 未知のラベルが指定された場合
     */
    @JsonCreator
    public static ActionType fromLabel(String label) {
        for (ActionType t : values()) {
            if (t.label.equalsIgnoreCase(label)) return t;
        }
        throw new IllegalArgumentException("Unknown action type: " + label);
    }
}
