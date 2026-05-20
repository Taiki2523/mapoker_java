package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 管理者による残高付与リクエストです。
 *
 * @param targetUsername 付与対象ユーザー名
 * @param amount 付与額
 */
public record AdminGrantRequest(
        @JsonProperty("target_username") @NotBlank String targetUsername,
        @JsonProperty("amount") @Positive long amount
) {
}
