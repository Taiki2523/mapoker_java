package com.mapoker.infrastructure.persistence;

import com.mapoker.application.history.HandHistoryEntry;
import com.mapoker.application.ports.HandHistoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring の {@code @Repository} としてローカル実行向けにハンド履歴をメモリ保持する実装です。
 */
@Repository
@Profile("local")
public class InMemoryHandHistoryRepository implements HandHistoryRepository {

    private final Map<String, List<HandHistoryEntry>> store = new ConcurrentHashMap<>();

    /**
     * ハンド履歴を保存します。
     *
     * @param entry 保存するハンド履歴
     */
    @Override
    public void save(HandHistoryEntry entry) {
        List<HandHistoryEntry> entries = new ArrayList<>(store.getOrDefault(entry.tableId(), List.of()));
        entries.add(entry);
        entries.sort(Comparator.comparing(HandHistoryEntry::finishedAt).reversed());
        store.put(entry.tableId(), entries);
    }

    /**
     * 指定テーブル群の直近ハンド履歴を取得します。
     *
     * @param tableIds 対象テーブル ID の一覧
     * @param limit 取得件数の上限
     * @return 直近ハンド履歴一覧
     */
    @Override
    public List<HandHistoryEntry> findRecentByTableIds(List<String> tableIds, int limit) {
        if (tableIds == null || tableIds.isEmpty()) {
            return List.of();
        }
        return new LinkedHashSet<>(tableIds).stream()
                .flatMap(tableId -> store.getOrDefault(tableId, List.of()).stream())
                .sorted(Comparator.comparing(HandHistoryEntry::finishedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }
}
