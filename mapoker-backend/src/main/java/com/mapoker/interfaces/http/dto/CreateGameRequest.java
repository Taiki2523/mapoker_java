package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.domain.game.OddChipRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * ゲーム作成リクエストです。
 *
 * @param players プレイヤー一覧
 * @param buttonIndex ボタン位置
 * @param bigBlind ビッグブラインド額
 * @param seed 乱数シード
 * @param oddChipRule 端数チップルール
 */
public record CreateGameRequest(
        @NotEmpty List<@Valid PlayerDto> players,
        @JsonProperty("button_index") @Min(0) int buttonIndex,
        @JsonProperty("big_blind") @Positive int bigBlind,
        Long seed,
        @JsonProperty("odd_chip_rule") OddChipRule oddChipRule
) {
    /**
     * ゲーム作成時のプレイヤー DTO です。
     *
     * @param id プレイヤー ID
     * @param stack 初期スタック
     */
    public record PlayerDto(
            @NotBlank String id,
            @Positive int stack
    ) {}
}
