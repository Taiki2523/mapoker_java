package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.rules.ActionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 行動適用リクエストです。
 *
 * @param playerIndex 行動するプレイヤー位置
 * @param action 実行する行動
 */
public record ApplyActionRequest(
        @JsonProperty("player_index") @Min(0) int playerIndex,
        @NotNull @Valid ActionDto action
) {
    /**
     * 行動内容 DTO です。
     *
     * @param type 行動種別
     * @param amount 行動金額
     */
    public record ActionDto(
            @NotNull ActionType type,
            @PositiveOrZero int amount
    ) {}
}
