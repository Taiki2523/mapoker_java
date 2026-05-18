package com.mapoker.application.history;
import com.mapoker.application.history.UserTableHistoryEntry;
import com.mapoker.application.ports.UserTableHistoryRepository;
import com.mapoker.application.table.TableMemberRecord;
import com.mapoker.application.table.TableRecord;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring の {@code @Service} としてユーザーのテーブル参加履歴を管理するサービスです。
 */
@Service
public class UserTableHistoryService {

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final UserTableHistoryRepository historyRepository;

    public UserTableHistoryService(UserTableHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * テーブル参加を履歴へ記録します。
     *
     * @param username ユーザー名
     * @param table 参加テーブル
     * @param seatIndex 着席位置
     */
    public void recordJoin(String username, TableRecord table, int seatIndex) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return;
        }
        historyRepository.recordJoin(normalizedUsername, table, seatIndex);
    }

    /**
     * テーブル退出を履歴へ記録します。
     *
     * @param username ユーザー名
     * @param tableId テーブル ID
     * @param seatIndex 着席位置
     */
    public void recordLeave(String username, String tableId, Integer seatIndex) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null || tableId == null || tableId.isBlank()) {
            return;
        }
        historyRepository.recordLeave(normalizedUsername, tableId, seatIndex);
    }

    /**
     * 既定件数で直近参加履歴を取得します。
     *
     * @param username ユーザー名
     * @return 直近参加履歴一覧
     */
    public List<UserTableHistoryEntry> listRecent(String username) {
        return listRecent(username, DEFAULT_HISTORY_LIMIT);
    }

    /**
     * 直近参加履歴を取得します。
     *
     * @param username ユーザー名
     * @param limit 取得件数の上限
     * @return 直近参加履歴一覧
     */
    public List<UserTableHistoryEntry> listRecent(String username, int limit) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return List.of();
        }
        return historyRepository.findRecentByUsername(normalizedUsername, Math.max(1, limit));
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim();
    }
}
