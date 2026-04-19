package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.ActionRecord;
import com.mapoker.domain.rules.ActionType;

import java.util.List;

public record ActionsResponse(List<ActionDto> actions) {
    public record ActionDto(
            int seq,
            @JsonProperty("player_index") int playerIndex,
            ActionType type,
            int amount
    ) {}

    public static ActionsResponse from(List<ActionRecord> records) {
        List<ActionDto> dtos = records.stream()
                .map(r -> new ActionDto(r.seq(), r.playerIndex(), r.actionType(), r.amount()))
                .toList();
        return new ActionsResponse(dtos);
    }
}
