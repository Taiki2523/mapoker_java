package com.mapoker.application.history;

import java.time.Instant;
import java.util.List;

/**
 * ユーザーごとのテーブル参加履歴を表すレコードです。
 *
 * @param username ユーザー名
 * @param tableId テーブル ID
 * @param tableName テーブル名
 * @param seatIndex 着席位置
 * @param visibility 公開設定
 * @param status テーブル状態
 * @param flags テーブル属性の一覧
 * @param joinedAt 参加日時
 * @param leftAt 退出日時
 */
public record UserTableHistoryEntry(
        String username,
        String tableId,
        String tableName,
        int seatIndex,
        String visibility,
        String status,
        List<String> flags,
        Instant joinedAt,
        Instant leftAt
) {
    public UserTableHistoryEntry {
        flags = flags == null ? List.of() : List.copyOf(flags);
    }

    /**
     * 参加履歴が現在も有効かを返します。
     *
     * @return 退出日時が未設定なら {@code true}
     */
    public boolean active() {
        return leftAt == null;
    }
}
