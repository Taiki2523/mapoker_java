package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.history.UserTableHistoryEntry;

import java.util.List;

/**
 * ユーザーのテーブル参加履歴レスポンスです。
 *
 * @param tableId テーブル ID
 * @param tableName テーブル名
 * @param seatIndex 着席位置
 * @param visibility 公開設定
 * @param status テーブル状態
 * @param flags テーブル属性一覧
 * @param joinedAt 参加日時
 * @param leftAt 退出日時
 * @param active 現在も参加中かどうか
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserTableHistoryResponse(
        @JsonProperty("table_id") String tableId,
        @JsonProperty("table_name") String tableName,
        @JsonProperty("seat_index") int seatIndex,
        String visibility,
        String status,
        List<String> flags,
        @JsonProperty("joined_at") String joinedAt,
        @JsonProperty("left_at") String leftAt,
        boolean active
) {
    /**
     * 参加履歴からレスポンスを生成します。
     *
     * @param entry 参加履歴
     * @return 生成したレスポンス
     */
    public static UserTableHistoryResponse from(UserTableHistoryEntry entry) {
        return new UserTableHistoryResponse(
                entry.tableId(),
                entry.tableName(),
                entry.seatIndex(),
                entry.visibility(),
                entry.status(),
                entry.flags(),
                entry.joinedAt().toString(),
                entry.leftAt() != null ? entry.leftAt().toString() : null,
                entry.active()
        );
    }
}
