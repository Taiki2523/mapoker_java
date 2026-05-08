package com.mapoker.infrastructure.persistence;

import com.mapoker.application.WalletEntry;
import com.mapoker.application.WalletLedgerEntry;
import com.mapoker.application.WalletRepository;
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

    /**
     * ユーザーウォレットを初期化します。
     *
     * @param username ユーザー名
     */
    @Override
    @Transactional
    public void initializeWallet(String username) {
        String idempotencyKey = "REGISTER_BONUS:" + username;
        jdbc.update("""
                WITH inserted_wallet AS (
                  INSERT INTO user_wallets (user_id, chip_balance)
                  SELECT id, ?
                  FROM users
                  WHERE username = ?
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
                username,
                walletProperties.initialGrant(),
                "REGISTER_BONUS",
                "USER",
                username,
                idempotencyKey);
    }

    /**
     * ユーザー名からウォレット残高を取得します。
     *
     * @param username ユーザー名
     * @return ウォレット情報
     */
    @Override
    public Optional<WalletEntry> findByUsername(String username) {
        List<WalletEntry> results = jdbc.query("""
                SELECT u.username, uw.chip_balance, uw.updated_at
                FROM user_wallets uw
                JOIN users u ON u.id = uw.user_id
                WHERE u.username = ?
                """,
                (rs, rowNum) -> mapWalletEntry(rs),
                username);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * 指定額を出金します。
     *
     * @param username ユーザー名
     * @param amount 出金額
     * @param reason 取引理由
     * @param refType 参照種別
     * @param refId 参照 ID
     * @param idempotencyKey 冪等性キー
     * @return 出金できた場合は {@code true}
     */
    @Override
    @Transactional
    public boolean debit(String username, long amount, String reason, String refType, String refId, String idempotencyKey) {
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
                      WHERE user_id = (SELECT id FROM users WHERE username = ?)
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
                    username,
                    amount,
                    -amount,
                    reason,
                    refType,
                    refId,
                    idempotencyKey);
            if (inserted > 0) {
                return true;
            }
            return hasLedgerEntry(idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            if (hasLedgerEntry(idempotencyKey)) {
                return true;
            }
            throw e;
        }
    }

    /**
     * 指定額を入金します。
     *
     * @param username ユーザー名
     * @param amount 入金額
     * @param reason 取引理由
     * @param refType 参照種別
     * @param refId 参照 ID
     * @param idempotencyKey 冪等性キー
     * @throws IllegalStateException ウォレットが存在しない場合
     */
    @Override
    @Transactional
    public void credit(String username, long amount, String reason, String refType, String refId, String idempotencyKey) {
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
                      WHERE user_id = (SELECT id FROM users WHERE username = ?)
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
                    username,
                    amount,
                    reason,
                    refType,
                    refId,
                    idempotencyKey);
            if (inserted == 0 && !hasLedgerEntry(idempotencyKey)) {
                throw new IllegalStateException("wallet not found: " + username);
            }
        } catch (DataIntegrityViolationException e) {
            if (!hasLedgerEntry(idempotencyKey)) {
                throw e;
            }
        }
    }

    /**
     * 直近の台帳履歴を取得します。
     *
     * @param username ユーザー名
     * @param limit 取得件数の上限
     * @return 台帳履歴一覧
     */
    @Override
    public List<WalletLedgerEntry> findLedger(String username, int limit) {
        return jdbc.query("""
                SELECT wl.id, u.username, wl.delta, wl.balance_after, wl.reason, wl.reference_type, wl.reference_id, wl.created_at
                FROM wallet_ledger wl
                JOIN users u ON u.id = wl.user_id
                WHERE u.username = ?
                ORDER BY wl.created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapLedgerEntry(rs),
                username,
                Math.max(1, limit));
    }

    /**
     * 指定理由の最終台帳時刻を取得します。
     *
     * @param username ユーザー名
     * @param reason 取引理由
     * @return 最終台帳時刻
     */
    @Override
    public Optional<Instant> findLastLedgerTime(String username, String reason) {
        List<Instant> results = jdbc.query("""
                SELECT MAX(wl.created_at) AS last_created_at
                FROM wallet_ledger wl
                JOIN users u ON u.id = wl.user_id
                WHERE u.username = ? AND wl.reason = CAST(? AS wallet_ledger_reason)
                """,
                (rs, rowNum) -> {
                    Timestamp timestamp = rs.getTimestamp("last_created_at");
                    return timestamp != null ? timestamp.toInstant() : null;
                },
                username,
                reason);
        if (results.isEmpty() || results.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    private boolean hasLedgerEntry(String idempotencyKey) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM wallet_ledger
                WHERE idempotency_key = ?
                """,
                Integer.class,
                idempotencyKey);
        return count != null && count > 0;
    }

    private WalletEntry mapWalletEntry(ResultSet rs) throws SQLException {
        return new WalletEntry(
                rs.getString("username"),
                rs.getLong("chip_balance"),
                rs.getTimestamp("updated_at").toInstant()
        );
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
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
