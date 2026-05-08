package com.mapoker.application;

import java.util.List;

/**
 * ハンド履歴の永続化と取得を担当するリポジトリです。
 */
public interface HandHistoryRepository {

    /**
     * ハンド履歴を保存します。
     *
     * @param entry 保存するハンド履歴
     */
    void save(HandHistoryEntry entry);

    /**
     * 指定したテーブル群の直近ハンド履歴を取得します。
     *
     * @param tableIds 対象テーブル ID の一覧
     * @param limit 取得件数の上限
     * @return 新しい順のハンド履歴一覧
     */
    List<HandHistoryEntry> findRecentByTableIds(List<String> tableIds, int limit);
}
