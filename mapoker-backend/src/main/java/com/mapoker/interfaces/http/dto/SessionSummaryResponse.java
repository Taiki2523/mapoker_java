package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.table.OfflineSessionService.PlayerSummary;

import java.util.List;

public record SessionSummaryResponse(
        @JsonProperty("table_id") String tableId,
        List<PlayerEntry> players
) {
    public static SessionSummaryResponse from(String tableId, List<PlayerSummary> summaries) {
        List<PlayerEntry> entries = summaries.stream()
                .map(s -> new PlayerEntry(
                        s.seatIndex(), s.displayName(),
                        s.buyIn(), s.cashOut(), s.profit(), s.settled()))
                .toList();
        return new SessionSummaryResponse(tableId, entries);
    }

    public record PlayerEntry(
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("buy_in") int buyIn,
            @JsonProperty("cash_out") int cashOut,
            int profit,
            boolean settled
    ) {}
}
