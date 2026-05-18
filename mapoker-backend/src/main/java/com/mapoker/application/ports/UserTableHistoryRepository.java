package com.mapoker.application.ports;

import com.mapoker.application.table.TableRecord;
import com.mapoker.application.history.UserTableHistoryEntry;

import java.util.List;

/**
 * ユーザーのテーブル参加履歴を扱うリポジトリです。
 */
public interface UserTableHistoryRepository {

    /**
     * テーブル参加を履歴として記録します。
     *
     * @param username ユーザー名
     * @param table 参加したテーブル
     * @param seatIndex 着席位置
     */
    void recordJoin(String username, TableRecord table, int seatIndex);

    /**
     * テーブル退出を履歴として記録します。
     *
     * @param username ユーザー名
     * @param tableId テーブル ID
     * @param seatIndex 着席位置
     */
    void recordLeave(String username, String tableId, Integer seatIndex);

    /**
     * ユーザーの直近参加履歴を取得します。
     *
     * @param username ユーザー名
     * @param limit 取得件数の上限
     * @return 参加履歴一覧
     */
    List<UserTableHistoryEntry> findRecentByUsername(String username, int limit);
}
