package com.mapoker.application;

import com.mapoker.infrastructure.config.WalletProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Spring の {@code @Service} としてウォレット残高、入出金、ボーナス付与を管理するサービスです。
 */
@Service
@Profile("postgresql")
public class WalletService {

    private static final String DAILY_BONUS = "DAILY_BONUS";
    private static final String RECOVERY_BONUS = "RECOVERY_BONUS";
    private static final String TABLE_BUY_IN = "TABLE_BUY_IN";
    private static final String TABLE_CASH_OUT = "TABLE_CASH_OUT";
    private static final String ADMIN_GRANT = "ADMIN_GRANT";

    private final WalletRepository walletRepository;
    private final WalletProperties walletProperties;

    public WalletService(WalletRepository walletRepository, WalletProperties walletProperties) {
        this.walletRepository = walletRepository;
        this.walletProperties = walletProperties;
    }

    /**
     * ユーザーのウォレットを初期化します。
     *
     * @param username ユーザー名
     * @throws IllegalArgumentException ユーザー名が不正な場合
     */
    public void initializeWallet(String username) {
        walletRepository.initializeWallet(requireUsername(username));
    }

    /**
     * 現在の残高を取得します。
     *
     * @param username ユーザー名
     * @return ウォレット残高情報
     */
    public WalletEntry getBalance(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return new WalletEntry(null, 0L, null);
        }
        return walletRepository.findByUsername(normalizedUsername)
                .orElseGet(() -> new WalletEntry(normalizedUsername, 0L, null));
    }

    /**
     * 台帳履歴を取得します。
     *
     * @param username ユーザー名
     * @param limit 取得件数の上限
     * @return 台帳履歴一覧
     */
    public List<WalletLedgerEntry> getLedger(String username, int limit) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return List.of();
        }
        return walletRepository.findLedger(normalizedUsername, Math.max(1, limit));
    }

    /**
     * 次回請求可能時刻を取得します。
     *
     * @param username ユーザー名
     * @return 日次ボーナスと救済ボーナスの次回可能時刻
     */
    public NextClaimTimes getNextClaimTimes(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return new NextClaimTimes(null, null);
        }
        WalletEntry wallet = getBalance(normalizedUsername);
        return new NextClaimTimes(
                nextClaimAt(normalizedUsername, DAILY_BONUS, walletProperties.dailyBonusCooldownHours()),
                nextRecoveryAt(normalizedUsername, wallet));
    }

    /**
     * テーブル参加のためにウォレットからチップを引き落とします。
     *
     * @param username ユーザー名
     * @param tableId テーブル ID
     * @param amount 引き落とし額
     * @throws IllegalArgumentException 入力値が不正な場合
     * @throws IllegalStateException 残高不足の場合
     */
    public void buyIn(String username, String tableId, int amount) {
        String normalizedUsername = requireUsername(username);
        String normalizedTableId = normalizeReference(tableId);
        validateAmount(amount);
        if (!walletRepository.debit(
                normalizedUsername,
                amount,
                TABLE_BUY_IN,
                "TABLE",
                normalizedTableId,
                "BUY_IN:" + normalizedTableId + ":" + normalizedUsername)) {
            throw new IllegalStateException("insufficient funds");
        }
    }

    /**
     * テーブルからの持ち帰りチップをウォレットへ戻します。
     *
     * @param username ユーザー名
     * @param tableId テーブル ID
     * @param amount 入金額
     * @throws IllegalArgumentException 入力値が不正な場合
     */
    public void cashOut(String username, String tableId, int amount) {
        String normalizedUsername = requireUsername(username);
        String normalizedTableId = normalizeReference(tableId);
        validateAmount(amount);
        walletRepository.credit(
                normalizedUsername,
                amount,
                TABLE_CASH_OUT,
                "TABLE",
                normalizedTableId,
                "CASH_OUT:" + normalizedTableId + ":" + normalizedUsername + ":" + amount);
    }

    /**
     * 日次ボーナスを付与します。
     *
     * @param username ユーザー名
     * @throws IllegalArgumentException ユーザー名が不正な場合
     * @throws IllegalStateException クールダウン中の場合
     */
    public void claimDailyBonus(String username) {
        String normalizedUsername = requireUsername(username);
        requireCooldownElapsed(
                normalizedUsername,
                DAILY_BONUS,
                walletProperties.dailyBonusCooldownHours());
        walletRepository.credit(
                normalizedUsername,
                walletProperties.dailyBonusAmount(),
                DAILY_BONUS,
                "SYSTEM",
                normalizedUsername,
                "DAILY_BONUS:" + normalizedUsername + ":" + currentWindow(walletProperties.dailyBonusCooldownHours()));
    }

    /**
     * 救済ボーナスを付与します。
     *
     * @param username ユーザー名
     * @throws IllegalArgumentException ユーザー名が不正な場合
     * @throws IllegalStateException 条件未達またはクールダウン中の場合
     */
    public void claimRecovery(String username) {
        String normalizedUsername = requireUsername(username);
        WalletEntry wallet = getBalance(normalizedUsername);
        if (wallet.chipBalance() > walletProperties.recoveryThreshold()) {
            throw new IllegalStateException("recovery not available");
        }
        requireCooldownElapsed(
                normalizedUsername,
                RECOVERY_BONUS,
                walletProperties.recoveryCooldownHours());
        walletRepository.credit(
                normalizedUsername,
                walletProperties.recoveryAmount(),
                RECOVERY_BONUS,
                "SYSTEM",
                normalizedUsername,
                "RECOVERY_BONUS:" + normalizedUsername + ":" + currentWindow(walletProperties.recoveryCooldownHours()));
    }

    /**
     * 管理者権限で残高を付与します。
     *
     * @param adminUsername 管理者ユーザー名
     * @param targetUsername 付与対象ユーザー名
     * @param amount 付与額
     * @throws IllegalArgumentException 入力値が不正な場合
     * @throws AccessDeniedException 管理者権限がない場合
     */
    public void adminGrant(String adminUsername, String targetUsername, long amount) {
        String normalizedAdminUsername = requireUsername(adminUsername);
        String normalizedTargetUsername = requireUsername(targetUsername);
        if (!walletProperties.adminUsernames().contains(normalizedAdminUsername)) {
            throw new AccessDeniedException("admin access required");
        }
        validateAmount(amount);
        walletRepository.credit(
                normalizedTargetUsername,
                amount,
                ADMIN_GRANT,
                "ADMIN",
                normalizedAdminUsername,
                "ADMIN_GRANT:" + normalizedAdminUsername + ":" + normalizedTargetUsername + ":" + Instant.now().toEpochMilli());
    }

    private Instant nextRecoveryAt(String username, WalletEntry wallet) {
        if (wallet.chipBalance() > walletProperties.recoveryThreshold()) {
            return null;
        }
        return nextClaimAt(username, RECOVERY_BONUS, walletProperties.recoveryCooldownHours());
    }

    private Instant nextClaimAt(String username, String reason, int cooldownHours) {
        Instant now = Instant.now();
        return walletRepository.findLastLedgerTime(username, reason)
                .map(lastClaimAt -> lastClaimAt.plus(cooldownHours, ChronoUnit.HOURS))
                .filter(nextClaimAt -> nextClaimAt.isAfter(now))
                .orElse(null);
    }

    private void requireCooldownElapsed(String username, String reason, int cooldownHours) {
        Instant cutoff = Instant.now().minus(cooldownHours, ChronoUnit.HOURS);
        walletRepository.findLastLedgerTime(username, reason)
                .filter(lastClaimAt -> lastClaimAt.isAfter(cutoff))
                .ifPresent(ignored -> {
                    throw new IllegalStateException(reason + " cooldown active");
                });
    }

    private long currentWindow(int cooldownHours) {
        long windowMillis = ChronoUnit.HOURS.getDuration().multipliedBy(cooldownHours).toMillis();
        return Instant.now().toEpochMilli() / windowMillis;
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim();
    }

    private String requireUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("username is required");
        }
        return normalizedUsername;
    }

    private String normalizeReference(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reference is required");
        }
        return value.trim();
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    /**
     * 次回請求可能時刻をまとめて返すレコードです。
     *
     * @param nextDailyBonusAt 日次ボーナスの次回請求可能時刻
     * @param nextRecoveryAt 救済ボーナスの次回請求可能時刻
     */
    public record NextClaimTimes(
            Instant nextDailyBonusAt,
            Instant nextRecoveryAt
    ) {
    }
}
