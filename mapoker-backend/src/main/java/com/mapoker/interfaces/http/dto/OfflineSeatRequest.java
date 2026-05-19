package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OfflineSeatRequest(
        @JsonProperty("seat_index") @Min(0) int seatIndex,
        @NotBlank String name,
        @JsonProperty("buy_in") @Min(1) int buyIn
) {}
