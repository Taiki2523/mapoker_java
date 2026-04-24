package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AdminGrantRequest(
        @JsonProperty("target_username") @NotBlank String targetUsername,
        @JsonProperty("amount") @Positive long amount
) {
}
