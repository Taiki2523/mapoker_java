package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.game.OddChipRule;
import java.util.List;

public record CreateGameRequest(
        List<PlayerDto> players,
        @JsonProperty("button_index") int buttonIndex,
        @JsonProperty("big_blind") int bigBlind,
        Long seed,
        @JsonProperty("odd_chip_rule") OddChipRule oddChipRule
) {
    public record PlayerDto(String id, int stack) {}
}
