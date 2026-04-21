package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.UserTableHistoryEntry;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserTableHistoryResponse(
        @JsonProperty("table_id") String tableId,
        @JsonProperty("table_name") String tableName,
        @JsonProperty("seat_index") int seatIndex,
        String visibility,
        String status,
        List<String> flags,
        @JsonProperty("joined_at") String joinedAt,
        @JsonProperty("left_at") String leftAt,
        boolean active
) {
    public static UserTableHistoryResponse from(UserTableHistoryEntry entry) {
        return new UserTableHistoryResponse(
                entry.tableId(),
                entry.tableName(),
                entry.seatIndex(),
                entry.visibility(),
                entry.status(),
                entry.flags(),
                entry.joinedAt().toString(),
                entry.leftAt() != null ? entry.leftAt().toString() : null,
                entry.active()
        );
    }
}
