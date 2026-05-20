package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeclareShowdownRequest(
        @JsonProperty("player_index") int playerIndex,
        boolean win
) {}
