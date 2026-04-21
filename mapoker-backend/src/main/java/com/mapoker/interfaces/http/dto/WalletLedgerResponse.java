package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.WalletLedgerEntry;

import java.time.Instant;

public record WalletLedgerResponse(
        @JsonProperty("id") long id,
        @JsonProperty("delta") long delta,
        @JsonProperty("balance_after") long balanceAfter,
        @JsonProperty("reason") String reason,
        @JsonProperty("reference_type") String referenceType,
        @JsonProperty("reference_id") String referenceId,
        @JsonProperty("created_at") Instant createdAt
) {
    public static WalletLedgerResponse from(WalletLedgerEntry entry) {
        return new WalletLedgerResponse(
                entry.id(),
                entry.delta(),
                entry.balanceAfter(),
                entry.reason(),
                entry.referenceType(),
                entry.referenceId(),
                entry.createdAt());
    }
}
