package com.mapoker.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalletRepository {

    void initializeWallet(String username);

    Optional<WalletEntry> findByUsername(String username);

    boolean debit(String username, long amount, String reason, String refType, String refId, String idempotencyKey);

    void credit(String username, long amount, String reason, String refType, String refId, String idempotencyKey);

    List<WalletLedgerEntry> findLedger(String username, int limit);

    Optional<Instant> findLastLedgerTime(String username, String reason);
}
