package com.mapoker.domain.card;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * トランプのスーツ（マーク）を表す列挙型。
 *
 * <p>各定数は1文字のコード（{@code "S"}/{@code "H"}/{@code "D"}/{@code "C"}）を持つ。
 * フラッシュ判定では {@code ordinal()} を配列インデックスとして使うため、
 * 定数の順序を変更しないこと。
 */
public enum Suit {
    SPADES("S"), HEARTS("H"), DIAMONDS("D"), CLUBS("C");

    private final String code;

    Suit(String code) {
        this.code = code;
    }

    /**
     * JSON シリアライズおよびカードコード文字列で使う1文字のコードを返す。
     *
     * @return スーツの文字コード（{@code "S"}、{@code "H"}、{@code "D"}、{@code "C"} のいずれか）
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 文字コードから {@link Suit} を返す。大文字小文字を区別しない。
     * Jackson の {@code @JsonCreator} として使用される。
     *
     * @param code スーツを表す文字列（例: {@code "S"}、{@code "h"}）
     * @return 対応する {@link Suit}
     * @throws IllegalArgumentException 不明なコードの場合
     */
    @JsonCreator
    public static Suit fromCode(String code) {
        for (Suit s : values()) {
            if (s.code.equalsIgnoreCase(code)) return s;
        }
        throw new IllegalArgumentException("Unknown suit: " + code);
    }
}
