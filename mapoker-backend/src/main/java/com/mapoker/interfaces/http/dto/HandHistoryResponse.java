package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.HandHistoryEntry;

import java.util.List;

public record HandHistoryResponse(
        @JsonProperty("table_id") String tableId,
        @JsonProperty("hand_id") String handId,
        List<PlayerResponse> players,
        List<Integer> winners,
        int pot,
        String street,
        @JsonProperty("finished_at") String finishedAt
) {
    public record PlayerResponse(
            String name,
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("stack_before") int stackBefore,
            @JsonProperty("stack_after") int stackAfter,
            boolean folded,
            @JsonProperty("hole_cards") List<String> holeCards
    ) {}

    public static HandHistoryResponse from(HandHistoryEntry entry) {
        return new HandHistoryResponse(
                entry.tableId(),
                entry.handId(),
                entry.players().stream()
                        .map(player -> new PlayerResponse(
                                player.name(),
                                player.seatIndex(),
                                player.stackBefore(),
                                player.stackAfter(),
                                player.folded(),
                                player.holeCards()))
                        .toList(),
                entry.winners(),
                entry.pot(),
                entry.street(),
                entry.finishedAt().toString()
        );
    }
}
