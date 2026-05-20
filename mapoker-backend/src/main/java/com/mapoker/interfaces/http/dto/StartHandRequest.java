package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;
import org.springframework.lang.Nullable;

/**
 * ハンド開始リクエストです。
 *
 * @param bigBlind ビッグブラインド額
 * @param straddle このハンドでUTGがストラドルするか（省略時は false）
 */
public record StartHandRequest(
        @JsonProperty("big_blind") @Positive int bigBlind,
        @Nullable Boolean straddle
) {
    public StartHandRequest(@JsonProperty("big_blind") @Positive int bigBlind) {
        this(bigBlind, null);
    }

    public boolean doStraddle() {
        return straddle != null && straddle;
    }
}
