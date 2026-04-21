package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.game.OddChipRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateGameRequest(
        @NotEmpty List<@Valid PlayerDto> players,
        @JsonProperty("button_index") @Min(0) int buttonIndex,
        @JsonProperty("big_blind") @Positive int bigBlind,
        Long seed,
        @JsonProperty("odd_chip_rule") OddChipRule oddChipRule
) {
    public record PlayerDto(
            @NotBlank String id,
            @Positive int stack
    ) {}
}
