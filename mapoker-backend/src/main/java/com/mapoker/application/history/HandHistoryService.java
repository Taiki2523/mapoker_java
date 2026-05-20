package com.mapoker.application.history;
import com.mapoker.application.table.TableRecord;
import com.mapoker.application.history.UserTableHistoryService;
import com.mapoker.application.history.UserTableHistoryEntry;
import com.mapoker.application.history.HandHistoryEntry;
import com.mapoker.application.ports.GameRepository;
import com.mapoker.application.ports.HandHistoryRepository;
import com.mapoker.application.game.ActionRecord;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring の {@code @Service} としてハンド履歴の記録と参照を仲介するサービスです。
 */
@Service
public class HandHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int TABLE_LOOKUP_LIMIT = 100;

    private final HandHistoryRepository handHistoryRepository;
    private final UserTableHistoryService userTableHistoryService;

    public HandHistoryService(HandHistoryRepository handHistoryRepository,
                              UserTableHistoryService userTableHistoryService) {
        this.handHistoryRepository = handHistoryRepository;
        this.userTableHistoryService = userTableHistoryService;
    }

    /**
     * ハンド履歴を記録します。
     *
     * @param entry 記録するハンド履歴
     */
    public void record(HandHistoryEntry entry) {
        if (entry == null || entry.tableId() == null || entry.tableId().isBlank()) {
            return;
        }
        handHistoryRepository.save(entry);
    }

    /**
     * ユーザーの直近ハンド履歴を既定件数で取得します。
     *
     * @param username 対象ユーザー名
     * @return 直近ハンド履歴の一覧
     */
    public List<HandHistoryEntry> listRecentForUser(String username) {
        return listRecentForUser(username, DEFAULT_LIMIT);
    }

    /**
     * ユーザーの直近ハンド履歴を取得します。
     *
     * @param username 対象ユーザー名
     * @param limit 取得件数の上限
     * @return 直近ハンド履歴の一覧
     */
    public List<HandHistoryEntry> listRecentForUser(String username, int limit) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return List.of();
        }
        int normalizedLimit = Math.max(1, limit);
        List<String> tableIds = userTableHistoryService
                .listRecent(normalizedUsername, Math.max(normalizedLimit, TABLE_LOOKUP_LIMIT)).stream()
                .map(UserTableHistoryEntry::tableId)
                .distinct()
                .toList();
        if (tableIds.isEmpty()) {
            return List.of();
        }
        return handHistoryRepository.findRecentByTableIds(tableIds, normalizedLimit);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim();
    }
}
