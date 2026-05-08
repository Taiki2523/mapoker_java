package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * テーブル作成リクエストです。
 *
 * @param tableName テーブル名
 * @param playerCount プレイヤー人数
 * @param smallBlind スモールブラインド額
 * @param bigBlind ビッグブラインド額
 * @param visibility 公開設定
 * @param flags テーブル属性の一覧
 */
public record CreateTableRequest(
        @JsonProperty("table_name") @Size(max = 100) String tableName,
        @JsonProperty("player_count") @Min(2) @Max(9) int playerCount,
        @JsonProperty("small_blind") @Positive @Nullable Integer smallBlind,
        @JsonProperty("big_blind") @Positive int bigBlind,
        @Pattern(regexp = "(?i)public|private", message = "must be public or private") String visibility,
        @Size(max = 8) List<@Pattern(regexp = "[a-z_]+", message = "must use lowercase snake_case") String> flags
) {
    public CreateTableRequest {
        if (smallBlind == null) {
            smallBlind = Math.max(1, bigBlind / 2);
        }
    }
}
