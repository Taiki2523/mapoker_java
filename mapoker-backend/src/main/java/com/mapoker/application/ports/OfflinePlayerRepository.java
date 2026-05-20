package com.mapoker.application.ports;

import java.time.Instant;
import java.util.List;

/**
 * オフラインセッション固有のプレイヤーデータ（バイイン・利確）を永続化するリポジトリポート。
 */
public interface OfflinePlayerRepository {

    /**
     * 指定席のバイイン額を累積加算する（リバイ対応）。
     */
    void addBuyIn(String gameId, int seatIndex, int amount);

    /**
     * 利確額とタイムスタンプを記録する。
     */
    void setCashout(String gameId, int seatIndex, int cashoutAmount, Instant cashoutAt);

    /**
     * ゲームIDに紐づく全プレイヤーのオフラインデータを返す。
     */
    List<OfflinePlayerData> findByGameId(String gameId);

    record OfflinePlayerData(
            int seatIndex,
            int offlineBuyIn,
            Integer offlineCashOut,
            Instant cashoutAt
    ) {}
}
