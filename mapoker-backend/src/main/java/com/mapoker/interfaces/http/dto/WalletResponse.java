package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.WalletEntry;
import com.mapoker.application.WalletService;

import java.time.Instant;

public record WalletResponse(
        @JsonProperty("chip_balance") long chipBalance,
        @JsonProperty("next_daily_bonus_at") Instant nextDailyBonusAt,
        @JsonProperty("next_recovery_at") Instant nextRecoveryAt
) {
    public static WalletResponse from(WalletEntry walletEntry, WalletService.NextClaimTimes nextClaimTimes) {
        return new WalletResponse(
                walletEntry.chipBalance(),
                nextClaimTimes.nextDailyBonusAt(),
                nextClaimTimes.nextRecoveryAt());
    }
}
