package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OfflineShowdownRequest(
        @JsonProperty("declared_winners") List<Integer> declaredWinners
) {}
