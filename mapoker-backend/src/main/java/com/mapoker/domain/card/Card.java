package com.mapoker.domain.card;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * トランプ1枚を表す値オブジェクト。
 *
 * <p>文字列表現（コード）は {@code "<rank><suit>"} 形式で、例えばスペードのエースは {@code "AS"}、
 * ハートの10は {@code "TH"}。Jackson シリアライズ／デシリアライズはこのコード形式を使う。
 */
public record Card(Rank rank, Suit suit) {

    /**
     * 文字列コードから {@link Card} を生成する。Jackson の {@code @JsonCreator} として使用される。
     *
     * <p>コード形式は {@code "<rank><suit>"}（2〜3文字）。
     * 例: {@code "AS"}（スペードのエース）、{@code "TH"}（ハートの10）、{@code "10D"}（ダイヤの10）。
     *
     * @param code カードを表す文字列（2〜3文字）
     * @return パースされた {@link Card}
     * @throws IllegalArgumentException コードが {@code null} または長さが不正な場合
     */
    @JsonCreator
    public static Card parse(String code) {
        if (code == null || code.length() < 2 || code.length() > 3) {
            throw new IllegalArgumentException("Invalid card: " + code);
        }
        String rankPart = code.substring(0, code.length() - 1);
        String suitPart = code.substring(code.length() - 1);
        return new Card(Rank.fromCode(rankPart), Suit.fromCode(suitPart));
    }

    /**
     * このカードの文字列コードを返す。Jackson の {@code @JsonValue} として使用される。
     *
     * @return {@code "<rank><suit>"} 形式の文字列（例: {@code "AS"}、{@code "TH"}）
     */
    @JsonValue
    public String toCode() {
        return rank.getCode() + suit.getCode();
    }
}
