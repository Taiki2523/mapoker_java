package com.mapoker.application.table;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * テーブルのインメモリ状態を一元管理するコンポーネント。
 * TableQueryService / TableMembershipService / TableLifecycleService が共有する。
 */
@Component
public class TableStore {

    final Map<String, TableRecord> tables = new ConcurrentHashMap<>();
    final Map<String, List<TableMemberRecord>> tableMembers = new ConcurrentHashMap<>();
    private final Map<String, Object> tableLocks = new ConcurrentHashMap<>();
    final Map<String, Instant> lastEmptiedAt = new ConcurrentHashMap<>();
    final Set<String> deletedTableIds = ConcurrentHashMap.newKeySet();

    public Object lock(String tableId) {
        return tableLocks.computeIfAbsent(tableId, ignored -> new Object());
    }

    public List<TableMemberRecord> getOrInitMembers(String tableId) {
        return tableMembers.computeIfAbsent(tableId, ignored -> new ArrayList<>());
    }

    public void trackEmptyTable(String tableId, List<TableMemberRecord> members) {
        if (members.isEmpty()) {
            lastEmptiedAt.putIfAbsent(tableId, Instant.now());
        } else {
            lastEmptiedAt.remove(tableId);
        }
    }
}
