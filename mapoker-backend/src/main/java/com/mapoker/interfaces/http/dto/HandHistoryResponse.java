package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.HandHistoryEntry;

import java.util.List;

/**
 * ハンド履歴レスポンスです。
 *
 * @param tableId テーブル ID
 * @param handId ハンド ID
 * @param players プレイヤー一覧
 * @param winners 勝者一覧
 * @param pot ポット額
 * @param street 終了ストリート
 * @param finishedAt 終了日時
 */
public record HandHistoryResponse(
        @JsonProperty("table_id") String tableId,
        @JsonProperty("hand_id") String handId,
        List<PlayerResponse> players,
        List<Integer> winners,
        int pot,
        String street,
        @JsonProperty("finished_at") String finishedAt
) {
    /**
     * ハンド履歴内プレイヤー表示 DTO です。
     *
     * @param name プレイヤー名
     * @param seatIndex 着席位置
     * @param stackBefore 開始前スタック
     * @param stackAfter 終了後スタック
     * @param folded フォールド済みかどうか
     * @param holeCards ホールカード文字列表現
     */
    public record PlayerResponse(
            String name,
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("stack_before") int stackBefore,
            @JsonProperty("stack_after") int stackAfter,
            boolean folded,
            @JsonProperty("hole_cards") List<String> holeCards
    ) {}

    /**
     * アプリケーション層の履歴からレスポンスを生成します。
     *
     * @param entry ハンド履歴
     * @return 生成したレスポンス
     */
    public static HandHistoryResponse from(HandHistoryEntry entry) {
        return new HandHistoryResponse(
                entry.tableId(),
                entry.handId(),
                entry.players().stream()
                        .map(player -> new PlayerResponse(
                                player.name(),
                                player.seatIndex(),
                                player.stackBefore(),
                                player.stackAfter(),
                                player.folded(),
                                player.holeCards()))
                        .toList(),
                entry.winners(),
                entry.pot(),
                entry.street(),
                entry.finishedAt().toString()
        );
    }
}
