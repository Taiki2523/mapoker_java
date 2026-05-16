package com.mapoker.application;

import com.mapoker.domain.rules.ActionType;

/**
 * 1回のプレイヤーアクションまたはショーダウン/ペイアウト結果を表す不変レコード。
 *
 * <p>{@link GameRepository} への永続化および履歴取得に使用される。
 * {@link ActionType#SHOWDOWN} の場合は {@code label} に役名を格納する。
 * {@link ActionType#PAYOUT} の場合は {@code amount} に獲得チップ数を格納する。
 *
 * @param seq         ゲーム内でのアクション通し番号（1始まり）
 * @param playerIndex アクションを実行したプレイヤーのシートインデックス
 * @param actionType  アクションの種別（{@link ActionType}）
 * @param amount      アクションに伴う金額（chips）。fold / check は 0
 * @param label       ショーダウン時の役名など補足情報（null 可）
 */
public record ActionRecord(int seq, int playerIndex, ActionType actionType, int amount, String label) {

    /** label なしの簡略コンストラクタ（既存コードとの後方互換）。 */
    public ActionRecord(int seq, int playerIndex, ActionType actionType, int amount) {
        this(seq, playerIndex, actionType, amount, null);
    }
}
