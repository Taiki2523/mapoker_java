package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.rules.ActionType;

public record ApplyActionRequest(
        @JsonProperty("player_index") int playerIndex,
        ActionDto action
) {
    public record ActionDto(ActionType type, int amount) {}
}
