package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.WalletEntry;
import com.mapoker.application.WalletService;

import java.time.Instant;

/**
 * ウォレット残高レスポンスです。
 *
 * @param chipBalance チップ残高
 * @param nextDailyBonusAt 次回日次ボーナス可能時刻
 */
public record WalletResponse(
        @JsonProperty("chip_balance") long chipBalance,
        @JsonProperty("next_daily_bonus_at") Instant nextDailyBonusAt
) {
    /**
     * ウォレット情報からレスポンスを生成します。
     *
     * @param walletEntry ウォレット情報
     * @param nextClaimTimes 次回請求可能時刻
     * @return 生成したレスポンス
     */
    public static WalletResponse from(WalletEntry walletEntry, WalletService.NextClaimTimes nextClaimTimes) {
        return new WalletResponse(
                walletEntry.chipBalance(),
                nextClaimTimes.nextDailyBonusAt());
    }
}
