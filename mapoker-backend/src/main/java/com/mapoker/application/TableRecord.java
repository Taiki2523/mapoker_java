package com.mapoker.application;

import java.time.Instant;
import java.util.List;

/**
 * テーブルの設定と状態を表すアプリケーション用レコードです。
 *
 * @param id テーブル ID
 * @param roomId ルーム ID
 * @param name テーブル名
 * @param gameType ゲーム種別
 * @param smallBlind スモールブラインド
 * @param bigBlind ビッグブラインド
 * @param minBuyIn 最小バイイン額
 * @param maxBuyIn 最大バイイン額
 * @param maxPlayers 最大参加人数
 * @param flags テーブル属性の一覧
 * @param visibility 公開設定
 * @param status テーブル状態
 * @param gameId 紐づくゲーム ID
 * @param createdAt 作成日時
 * @param everSeated 一度でも着席があったかどうか
 */
public record TableRecord(
        String id,
        String roomId,
        String name,
        String gameType,
        int smallBlind,
        int bigBlind,
        int minBuyIn,
        int maxBuyIn,
        int maxPlayers,
        List<String> flags,
        String visibility,
        String status,
        String gameId,
        Instant createdAt,
        boolean everSeated,
        int ante
) {
    public TableRecord {
        if (name == null || name.isBlank()) {
            name = "Cash Orbit Table";
        }
        if (gameType == null || gameType.isBlank()) {
            gameType = "ring";
        }
        flags = flags == null ? List.of() : List.copyOf(flags);
        if (visibility == null || visibility.isBlank()) {
            visibility = "public";
        }
        if (status == null || status.isBlank()) {
            status = "waiting";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        ante = Math.max(0, ante);
    }
}
