package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.TableMemberRecord;
import com.mapoker.application.TableRecord;

import java.util.List;

public record TableResponse(
        String id,
        @JsonProperty("room_id") String roomId,
        String name,
        @JsonProperty("game_type") String gameType,
        StakeDto stake,
        @JsonProperty("min_buy_in") int minBuyIn,
        @JsonProperty("max_buy_in") int maxBuyIn,
        @JsonProperty("max_players") int maxPlayers,
        List<String> flags,
        String visibility,
        String status,
        @JsonProperty("game_id") String gameId,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("member_count") int memberCount,
        List<MemberDto> members,
        GameResponse game
) {
    public record StakeDto(
            @JsonProperty("small_blind") int smallBlind,
            @JsonProperty("big_blind") int bigBlind
    ) {}

    public record MemberDto(
            String name,
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("joined_at") String joinedAt
    ) {}

    public static TableResponse from(TableRecord table, List<TableMemberRecord> members, GameResponse game) {
        List<MemberDto> memberDtos = members == null
                ? List.of()
                : members.stream()
                .map(member -> new MemberDto(member.name(), member.seatIndex(), member.joinedAt()))
                .toList();
        return new TableResponse(
                table.id(),
                table.roomId(),
                table.name(),
                table.gameType(),
                new StakeDto(table.smallBlind(), table.bigBlind()),
                table.minBuyIn(),
                table.maxBuyIn(),
                table.maxPlayers(),
                table.flags(),
                table.visibility(),
                table.status(),
                table.gameId(),
                table.createdAt().toString(),
                memberDtos.size(),
                memberDtos,
                game
        );
    }
}
