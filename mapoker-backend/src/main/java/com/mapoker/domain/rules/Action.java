package com.mapoker.domain.rules;

/**
 * プレイヤーが選択した行動を表すレコードです。
 *
 * @param type 行動種別
 * @param amount 行動に伴う金額
 */
public record Action(ActionType type, int amount) {

    /**
     * 行動インスタンスを生成します。
     *
     * @param type 行動種別
     * @param amount 行動に伴う金額
     * @return 生成した行動
     */
    public static Action of(ActionType type, int amount) {
        return new Action(type, amount);
    }
}
