package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;

public record OfflineCashoutRequest(
        @JsonProperty("seat_index") @Min(0) int seatIndex
) {}
