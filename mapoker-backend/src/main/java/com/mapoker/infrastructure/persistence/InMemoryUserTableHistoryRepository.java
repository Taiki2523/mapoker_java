package com.mapoker.infrastructure.persistence;

import com.mapoker.application.TableRecord;
import com.mapoker.application.UserTableHistoryEntry;
import com.mapoker.application.UserTableHistoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring の {@code @Repository} としてローカル実行向けに参加履歴をメモリ保持する実装です。
 */
@Repository
@Profile("local")
public class InMemoryUserTableHistoryRepository implements UserTableHistoryRepository {

    private final Map<String, List<UserTableHistoryEntry>> store = new ConcurrentHashMap<>();

    /**
     * テーブル参加を履歴へ記録します。
     *
     * @param username ユーザー名
     * @param table 参加テーブル
     * @param seatIndex 着席位置
     */
    @Override
    public void recordJoin(String username, TableRecord table, int seatIndex) {
        List<UserTableHistoryEntry> entries = new ArrayList<>(store.getOrDefault(username, List.of()));
        int existingIndex = findActiveEntry(entries, table.id(), seatIndex);
        Instant now = Instant.now();
        UserTableHistoryEntry entry = new UserTableHistoryEntry(
                username,
                table.id(),
                table.name(),
                seatIndex,
                table.visibility(),
                table.status(),
                table.flags(),
                existingIndex >= 0 ? entries.get(existingIndex).joinedAt() : now,
                null
        );
        if (existingIndex >= 0) {
            entries.set(existingIndex, entry);
        } else {
            entries.add(entry);
        }
        entries.sort(Comparator.comparing(UserTableHistoryEntry::joinedAt).reversed());
        store.put(username, entries);
    }

    /**
     * テーブル退出を履歴へ記録します。
     *
     * @param username ユーザー名
     * @param tableId テーブル ID
     * @param seatIndex 着席位置
     */
    @Override
    public void recordLeave(String username, String tableId, Integer seatIndex) {
        List<UserTableHistoryEntry> entries = new ArrayList<>(store.getOrDefault(username, List.of()));
        int index = findActiveEntry(entries, tableId, seatIndex);
        if (index < 0) {
            return;
        }
        UserTableHistoryEntry existing = entries.get(index);
        entries.set(index, new UserTableHistoryEntry(
                existing.username(),
                existing.tableId(),
                existing.tableName(),
                existing.seatIndex(),
                existing.visibility(),
                existing.status(),
                existing.flags(),
                existing.joinedAt(),
                Instant.now()
        ));
        entries.sort(Comparator.comparing(UserTableHistoryEntry::joinedAt).reversed());
        store.put(username, entries);
    }

    /**
     * ユーザーの直近参加履歴を取得します。
     *
     * @param username ユーザー名
     * @param limit 取得件数の上限
     * @return 参加履歴一覧
     */
    @Override
    public List<UserTableHistoryEntry> findRecentByUsername(String username, int limit) {
        return store.getOrDefault(username, List.of()).stream()
                .sorted(Comparator.comparing(UserTableHistoryEntry::joinedAt).reversed())
                .limit(limit)
                .toList();
    }

    private int findActiveEntry(List<UserTableHistoryEntry> entries, String tableId, Integer seatIndex) {
        for (int i = 0; i < entries.size(); i++) {
            UserTableHistoryEntry entry = entries.get(i);
            if (!entry.tableId().equals(tableId) || !entry.active()) {
                continue;
            }
            if (seatIndex != null && entry.seatIndex() != seatIndex) {
                continue;
            }
            return i;
        }
        return -1;
    }
}
