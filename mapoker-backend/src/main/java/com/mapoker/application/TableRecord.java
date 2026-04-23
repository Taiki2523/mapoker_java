package com.mapoker.application;

import java.time.Instant;
import java.util.List;

public record TableRecord(
        String id,
        String roomId,
        String name,
        String gameType,
        int smallBlind,
        int bigBlind,
        int minBuyIn,
        int maxBuyIn,
        int maxPlayers,
        List<String> flags,
        String visibility,
        String status,
        String gameId,
        Instant createdAt,
        boolean everSeated
) {
    public TableRecord {
        if (name == null || name.isBlank()) {
            name = "Cash Orbit Table";
        }
        if (gameType == null || gameType.isBlank()) {
            gameType = "ring";
        }
        flags = flags == null ? List.of() : List.copyOf(flags);
        if (visibility == null || visibility.isBlank()) {
            visibility = "public";
        }
        if (status == null || status.isBlank()) {
            status = "waiting";
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
