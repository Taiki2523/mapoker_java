package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TableMembershipRequest(
        @Size(max = 50) String name,
        @JsonProperty("seat_index") @Min(0) Integer seatIndex
) {}
