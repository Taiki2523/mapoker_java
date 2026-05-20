package com.mapoker.infrastructure.persistence;

import com.mapoker.application.wallet.WalletEntry;
import com.mapoker.application.wallet.WalletLedgerEntry;
import com.mapoker.application.ports.WalletRepository;
import com.mapoker.infrastructure.config.WalletProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring の {@code @Repository} として PostgreSQL へウォレット残高と台帳を保存する実装です。
 *
 * <p>ユーザー識別には {@code public_id}（UUID）を使用します。
 * {@code username} は一意でないため使用しません。
 */
@Repository
@Profile("postgresql")
public class PostgresWalletRepository implements WalletRepository {

    private final JdbcTemplate jdbc;
    private final WalletProperties walletProperties;

    public PostgresWalletRepository(JdbcTemplate jdbc, WalletProperties walletProperties) {
        this.jdbc = jdbc;
        this.walletProperties = walletProperties;
    }

    @Override
    @Transactional
    public void initializeWallet(String publicId) {
        String idempotencyKey = "REGISTER_BONUS:" + publicId;
        jdbc.update("""
                WITH inserted_wallet AS (
                  INSERT INTO user_wallets (user_id, chip_balance)
                  SELECT id, ?
                  FROM users
                  WHERE public_id = ?::uuid
                  ON CONFLICT (user_id) DO NOTHING
                  RETURNING user_id, chip_balance
                )
                INSERT INTO wallet_ledger (
                  user_id, delta, balance_after, reason, reference_type, reference_id, idempotency_key
                )
                SELECT
                  user_id,
                  ?,
                  chip_balance,
                  CAST(? AS wallet_ledger_reason),
                  ?,
                  ?,
                  ?
                FROM inserted_wallet
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                walletProperties.initialGrant(),
                publicId,
                walletProperties.initialGrant(),
                "REGISTER_BONUS",
                "USER",
                publicId,
                idempotencyKey);
    }

    @Override
    public Optional<WalletEntry> findByPublicId(String publicId) {
        List<WalletEntry> results = jdbc.query("""
                SELECT u.username, uw.chip_balance, uw.updated_at
                FROM user_wallets uw
                JOIN users u ON u.id = uw.user_id
                WHERE u.public_id = ?::uuid
                """,
                (rs, rowNum) -> mapWalletEntry(rs),
                publicId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    @Transactional
    public boolean debit(String publicId, long amount, String reason, String refType, String refId, String idempotencyKey) {
        try {
            int inserted = jdbc.update("""
                    WITH existing AS (
                      SELECT 1
                      FROM wallet_ledger
                      WHERE idempotency_key = ?
                    ),
                    updated AS (
                      UPDATE user_wallets
                      SET chip_balance = chip_balance - ?, updated_at = CURRENT_TIMESTAMP
                      WHERE user_id = (SELECT id FROM users WHERE public_id = ?::uuid)
                        AND chip_balance >= ?
                        AND NOT EXISTS (SELECT 1 FROM existing)
                      RETURNING user_id, chip_balance
                    )
                    INSERT INTO wallet_ledger (
                      user_id, delta, balance_after, reason, reference_type, reference_id, idempotency_key
                    )
                    SELECT
                      user_id,
                      ?,
                      chip_balance,
                      CAST(? AS wallet_ledger_reason),
                      ?,
                      ?,
                      ?
                    FROM updated
                    """,
                    idempotencyKey,
                    amount,
                    publicId,
                    amount,
                    -amount,
                    reason,
                    refType,
                    refId,
                    idempotencyKey);
            if (inserted > 0) return true;
            return hasLedgerEntry(idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            if (hasLedgerEntry(idempotencyKey)) return true;
            throw e;
        }
    }

    @Override
    @Transactional
    public void credit(String publicId, long amount, String reason, String refType, String refId, String idempotencyKey) {
        try {
            int inserted = jdbc.update("""
                    WITH existing AS (
                      SELECT 1
                      FROM wallet_ledger
                      WHERE idempotency_key = ?
                    ),
                    updated AS (
                      UPDATE user_wallets
                      SET chip_balance = chip_balance + ?, updated_at = CURRENT_TIMESTAMP
                      WHERE user_id = (SELECT id FROM users WHERE public_id = ?::uuid)
                        AND NOT EXISTS (SELECT 1 FROM existing)
                      RETURNING user_id, chip_balance
                    )
                    INSERT INTO wallet_ledger (
                      user_id, delta, balance_after, reason, reference_type, reference_id, idempotency_key
                    )
                    SELECT
                      user_id,
                      ?,
                      chip_balance,
                      CAST(? AS wallet_ledger_reason),
                      ?,
                      ?,
                      ?
                    FROM updated
                    """,
                    idempotencyKey,
                    amount,
                    publicId,
                    amount,
                    reason,
                    refType,
                    refId,
                    idempotencyKey);
            if (inserted == 0 && !hasLedgerEntry(idempotencyKey)) {
                throw new IllegalStateException("wallet not found: " + publicId);
            }
        } catch (DataIntegrityViolationException e) {
            if (!hasLedgerEntry(idempotencyKey)) throw e;
        }
    }

    @Override
    public List<WalletLedgerEntry> findLedger(String publicId, int limit) {
        return jdbc.query("""
                SELECT wl.id, u.username, wl.delta, wl.balance_after, wl.reason,
                       wl.reference_type, wl.reference_id, wl.created_at
                FROM wallet_ledger wl
                JOIN users u ON u.id = wl.user_id
                WHERE u.public_id = ?::uuid
                ORDER BY wl.created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapLedgerEntry(rs),
                publicId,
                Math.max(1, limit));
    }

    @Override
    public Optional<Instant> findLastLedgerTime(String publicId, String reason) {
        List<Instant> results = jdbc.query("""
                SELECT MAX(wl.created_at) AS last_created_at
                FROM wallet_ledger wl
                JOIN users u ON u.id = wl.user_id
                WHERE u.public_id = ?::uuid AND wl.reason = CAST(? AS wallet_ledger_reason)
                """,
                (rs, rowNum) -> {
                    Timestamp timestamp = rs.getTimestamp("last_created_at");
                    return timestamp != null ? timestamp.toInstant() : null;
                },
                publicId,
                reason);
        if (results.isEmpty() || results.get(0) == null) return Optional.empty();
        return Optional.of(results.get(0));
    }

    private boolean hasLedgerEntry(String idempotencyKey) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wallet_ledger WHERE idempotency_key = ?",
                Integer.class, idempotencyKey);
        return count != null && count > 0;
    }

    private WalletEntry mapWalletEntry(ResultSet rs) throws SQLException {
        return new WalletEntry(
                rs.getString("username"),
                rs.getLong("chip_balance"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private WalletLedgerEntry mapLedgerEntry(ResultSet rs) throws SQLException {
        return new WalletLedgerEntry(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getLong("delta"),
                rs.getLong("balance_after"),
                rs.getString("reason"),
                rs.getString("reference_type"),
                rs.getString("reference_id"),
                rs.getTimestamp("created_at").toInstant());
    }
}
