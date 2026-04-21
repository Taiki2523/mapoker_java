package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTableRequest(
        @JsonProperty("table_name") @Size(max = 100) String tableName,
        @JsonProperty("player_count") @Min(2) @Max(9) int playerCount,
        @JsonProperty("big_blind") @Positive int bigBlind,
        @Pattern(regexp = "(?i)public|private", message = "must be public or private") String visibility,
        @Size(max = 8) List<@Pattern(regexp = "[a-z_]+", message = "must use lowercase snake_case") String> flags
) {}
