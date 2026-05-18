package com.mapoker.application.history;

import java.time.Instant;
import java.util.List;

/**
 * 1ハンド分の履歴を保持する不変レコード。
 *
 * <p>ハンド終了時（fold-win またはショーダウン後）に {@link HandHistoryService#record} 経由で
 * {@link HandHistoryRepository} に永続化される。
 *
 * <p>{@code players} および {@code winners} は {@code null} を渡しても空リストに正規化される。
 *
 * @param tableId    テーブル（ゲーム）ID
 * @param handId     ハンド固有の UUID
 * @param players    各プレイヤーのスナップショット（{@link PlayerSnapshot}）
 * @param winners    勝者のシートインデックスリスト
 * @param pot        このハンドのトータルポット額（chips）
 * @param street     終了ストリートのラベル（例: {@code "preflop"}, {@code "river"}）
 * @param finishedAt ハンド終了時刻
 */
public record HandHistoryEntry(
        String tableId,
        String handId,
        List<PlayerSnapshot> players,
        List<Integer> winners,
        int pot,
        String street,
        Instant finishedAt
) {
    public HandHistoryEntry {
        players = players == null ? List.of() : List.copyOf(players);
        winners = winners == null ? List.of() : List.copyOf(winners);
    }

    /**
     * ハンド終了時点でのプレイヤーの状態スナップショット。
     *
     * <p>{@code name} が空の場合は {@code "Seat N"} にフォールバックする。
     * {@code holeCards} は履歴保存時点ではマスク済みの {@code "??"} で格納される。
     *
     * @param name        プレイヤー名（テーブルメンバー名、または座席 ID のフォールバック）
     * @param seatIndex   シートインデックス
     * @param stackBefore ハンド開始前のスタック額
     * @param stackAfter  ハンド終了後のスタック額
     * @param folded      フォールドしていた場合 {@code true}
     * @param holeCards   ホールカードのリスト（マスク済み）
     */
    public record PlayerSnapshot(
            String name,
            int seatIndex,
            int stackBefore,
            int stackAfter,
            boolean folded,
            List<String> holeCards
    ) {
        public PlayerSnapshot {
            if (name == null || name.isBlank()) {
                name = "Seat " + (seatIndex + 1);
            }
            holeCards = holeCards == null ? List.of() : List.copyOf(holeCards);
        }
    }
}
