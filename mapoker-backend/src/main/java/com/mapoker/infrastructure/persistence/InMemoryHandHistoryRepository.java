package com.mapoker.infrastructure.persistence;

import com.mapoker.application.HandHistoryEntry;
import com.mapoker.application.HandHistoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("local")
public class InMemoryHandHistoryRepository implements HandHistoryRepository {

    private final Map<String, List<HandHistoryEntry>> store = new ConcurrentHashMap<>();

    @Override
    public void save(HandHistoryEntry entry) {
        List<HandHistoryEntry> entries = new ArrayList<>(store.getOrDefault(entry.tableId(), List.of()));
        entries.add(entry);
        entries.sort(Comparator.comparing(HandHistoryEntry::finishedAt).reversed());
        store.put(entry.tableId(), entries);
    }

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
