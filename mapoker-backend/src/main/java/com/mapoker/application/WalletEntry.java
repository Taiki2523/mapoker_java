package com.mapoker.application;

import java.time.Instant;

public record WalletEntry(
        String username,
        long chipBalance,
        Instant updatedAt
) {
}
