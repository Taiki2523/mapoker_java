package com.mapoker.domain.hand;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ポーカーハンドの役を表す列挙型です。
 */
public enum HandRank {
    /** ハイカードです。 */
    HIGH_CARD("HighCard"),
    /** ワンペアです。 */
    ONE_PAIR("OnePair"),
    /** ツーペアです。 */
    TWO_PAIR("TwoPair"),
    /** スリーカードです。 */
    THREE_OF_A_KIND("ThreeOfAKind"),
    /** ストレートです。 */
    STRAIGHT("Straight"),
    /** フラッシュです。 */
    FLUSH("Flush"),
    /** フルハウスです。 */
    FULL_HOUSE("FullHouse"),
    /** フォーカードです。 */
    FOUR_OF_A_KIND("FourOfAKind"),
    /** ストレートフラッシュです。 */
    STRAIGHT_FLUSH("StraightFlush");

    private final String label;

    HandRank(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    /**
     * ラベルから役を解決します。
     *
     * @param label 役ラベル
     * @return 対応する役
     * @throws IllegalArgumentException 未知のラベルが指定された場合
     */
    @JsonCreator
    public static HandRank fromLabel(String label) {
        for (HandRank h : values()) {
            if (h.label.equalsIgnoreCase(label)) return h;
        }
        throw new IllegalArgumentException("Unknown hand rank: " + label);
    }
}
