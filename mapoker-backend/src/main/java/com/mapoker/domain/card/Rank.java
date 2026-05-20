package com.mapoker.domain.card;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * トランプのランク（数字・絵柄）を表す列挙型。
 *
 * <p>各定数は数値({@code value})と文字コード({@code code})を持つ。
 * 数値はハンド強さ比較および配列インデックスとして使用する（2〜14）。
 * エースは {@code 14} として扱い、ホイール（A-2-3-4-5）のストレートは
 * {@link com.mapoker.domain.hand.HandEvaluator} 内で特別処理する。
 */
public enum Rank {
    TWO(2, "2"), THREE(3, "3"), FOUR(4, "4"), FIVE(5, "5"),
    SIX(6, "6"), SEVEN(7, "7"), EIGHT(8, "8"), NINE(9, "9"),
    TEN(10, "T"), JACK(11, "J"), QUEEN(12, "Q"), KING(13, "K"), ACE(14, "A");

    private final int value;
    private final String code;

    Rank(int value, String code) {
        this.value = value;
        this.code = code;
    }

    /**
     * ハンド強さ比較や配列インデックスとして使う数値を返す。
     * 2〜14 の範囲で、エースは 14。
     *
     * @return ランクの数値（2〜14）
     */
    public int getValue() {
        return value;
    }

    /**
     * JSON シリアライズおよびカードコード文字列で使う1文字のコードを返す。
     * 10 は {@code "T"} として表現される。
     *
     * @return ランクの文字コード（例: {@code "A"}、{@code "T"}、{@code "2"}）
     */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * 文字コードから {@link Rank} を返す。Jackson の {@code @JsonCreator} として使用される。
     *
     * <p>{@code "10"} および大文字小文字を区別せず {@code "t"} はいずれも {@link #TEN} に対応する。
     *
     * @param code ランクを表す文字列（例: {@code "A"}、{@code "T"}、{@code "10"}）
     * @return 対応する {@link Rank}
     * @throws IllegalArgumentException 不明なコードの場合
     */
    @JsonCreator
    public static Rank fromCode(String code) {
        if ("10".equals(code) || "t".equalsIgnoreCase(code)) return TEN;
        for (Rank r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        throw new IllegalArgumentException("Unknown rank: " + code);
    }

    /**
     * 数値から {@link Rank} を返す。ハンド評価器内部での逆引きに使用する。
     *
     * @param value ランクの数値（2〜14）
     * @return 対応する {@link Rank}
     * @throws IllegalArgumentException 対応するランクが存在しない場合
     */
    public static Rank fromValue(int value) {
        for (Rank r : values()) {
            if (r.value == value) return r;
        }
        throw new IllegalArgumentException("Unknown rank value: " + value);
    }
}
