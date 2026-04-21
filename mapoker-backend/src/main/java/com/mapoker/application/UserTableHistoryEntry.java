package com.mapoker.application;

import java.time.Instant;
import java.util.List;

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

    public boolean active() {
        return leftAt == null;
    }
}
