package com.mapoker.application;

import java.time.Instant;
import java.util.List;

public record HandHistoryEntry(
        String tableId,
        String handId,
        List<PlayerSnapshot> players,
        List<Integer> winners,
        int pot,
        String street,
        Instant finishedAt
) {
    public HandHistoryEntry {
        players = players == null ? List.of() : List.copyOf(players);
        winners = winners == null ? List.of() : List.copyOf(winners);
    }

    public record PlayerSnapshot(
            String name,
            int seatIndex,
            int stackBefore,
            int stackAfter,
            boolean folded,
            List<String> holeCards
    ) {
        public PlayerSnapshot {
            if (name == null || name.isBlank()) {
                name = "Seat " + (seatIndex + 1);
            }
            holeCards = holeCards == null ? List.of() : List.copyOf(holeCards);
        }
    }
}
