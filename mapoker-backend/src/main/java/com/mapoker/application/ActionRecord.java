package com.mapoker.application;

import com.mapoker.domain.rules.ActionType;

/**
 * 1回のプレイヤーアクションを表す不変レコード。
 *
 * <p>{@link GameRepository} への永続化および履歴取得に使用される。
 * {@code amount} の意味は {@link ActionType} によって異なる。
 * {@code bet}/{@code raise} の場合は raise-to トータル額、{@code call} の場合は 0 で自動コール。
 *
 * @param seq         ゲーム内でのアクション通し番号（1始まり）
 * @param playerIndex アクションを実行したプレイヤーのシートインデックス
 * @param actionType  アクションの種別（{@link ActionType}）
 * @param amount      アクションに伴う金額（chips）。fold / check は 0
 */
public record ActionRecord(int seq, int playerIndex, ActionType actionType, int amount) {}
