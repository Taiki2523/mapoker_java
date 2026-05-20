package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.game.ActionRecord;
import com.mapoker.domain.rules.ActionType;

import java.util.List;

/**
 * アクション履歴レスポンスです。
 *
 * @param actions アクション一覧
 */
public record ActionsResponse(List<ActionDto> actions) {
    /**
     * 単一アクションの表示 DTO です。
     *
     * @param seq         連番
     * @param playerIndex プレイヤー位置
     * @param type        行動種別
     * @param amount      金額
     * @param label       補足ラベル（ショーダウン時の役名など、null 可）
     */
    public record ActionDto(
            int seq,
            @JsonProperty("player_index") int playerIndex,
            ActionType type,
            int amount,
            @JsonInclude(JsonInclude.Include.NON_NULL) String label
    ) {}

    /**
     * アプリケーション層の履歴からレスポンスを生成します。
     *
     * @param records アクション履歴
     * @return 生成したレスポンス
     */
    public static ActionsResponse from(List<ActionRecord> records) {
        List<ActionDto> dtos = records.stream()
                .map(r -> new ActionDto(r.seq(), r.playerIndex(), r.actionType(), r.amount(), r.label()))
                .toList();
        return new ActionsResponse(dtos);
    }
}
