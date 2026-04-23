package com.mapoker.application;

import java.time.Instant;

public record WalletLedgerEntry(
        long id,
        String username,
        long delta,
        long balanceAfter,
        String reason,
        String referenceType,
        String referenceId,
        Instant createdAt
) {
}
