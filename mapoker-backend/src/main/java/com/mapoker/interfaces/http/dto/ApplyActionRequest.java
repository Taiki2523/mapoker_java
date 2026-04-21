package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.rules.ActionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ApplyActionRequest(
        @JsonProperty("player_index") @Min(0) int playerIndex,
        @NotNull @Valid ActionDto action
) {
    public record ActionDto(
            @NotNull ActionType type,
            @PositiveOrZero int amount
    ) {}
}
