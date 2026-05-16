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
 *
 * <p>ユーザー識別には {@code publicId}（UUID）を使用します。
 * {@code username} は一意でないため、ウォレット操作には使用しません。
 */
@Service
@Profile("postgresql")
public class WalletService {

    private static final String DAILY_BONUS = "DAILY_BONUS";
    private static final String RECOVERY_BONUS = "RECOVERY_BONUS";
    private static final String TABLE_BUY_IN = "TABLE_BUY_IN";
    private static final String TABLE_REBUY = "TABLE_REBUY";
    private static final String TABLE_CASH_OUT = "TABLE_CASH_OUT";
    private static final String ADMIN_GRANT = "ADMIN_GRANT";

    private final WalletRepository walletRepository;
    private final WalletProperties walletProperties;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository,
                         WalletProperties walletProperties,
                         UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.walletProperties = walletProperties;
        this.userRepository = userRepository;
    }

    /**
     * ユーザーのウォレットを初期化します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     */
    public void initializeWallet(String publicId) {
        walletRepository.initializeWallet(requirePublicId(publicId));
    }

    /**
     * 現在の残高を取得します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @return ウォレット残高情報
     */
    public WalletEntry getBalance(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return new WalletEntry(null, 0L, null);
        }
        return walletRepository.findByPublicId(publicId.trim())
                .orElseGet(() -> new WalletEntry(publicId, 0L, null));
    }

    /**
     * 台帳履歴を取得します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @param limit    取得件数の上限
     * @return 台帳履歴一覧
     */
    public List<WalletLedgerEntry> getLedger(String publicId, int limit) {
        if (publicId == null || publicId.isBlank()) return List.of();
        return walletRepository.findLedger(publicId.trim(), Math.max(1, limit));
    }

    /**
     * 次回請求可能時刻を取得します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @return 日次ボーナスと救済ボーナスの次回可能時刻
     */
    public NextClaimTimes getNextClaimTimes(String publicId) {
        if (publicId == null || publicId.isBlank()) return new NextClaimTimes(null, null);
        WalletEntry wallet = getBalance(publicId);
        return new NextClaimTimes(
                nextClaimAt(publicId, DAILY_BONUS, walletProperties.dailyBonusCooldownHours()),
                nextRecoveryAt(publicId, wallet));
    }

    /**
     * テーブル参加のためにウォレットからチップを引き落とします。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @param tableId  テーブル ID
     * @param amount   引き落とし額
     */
    public void buyIn(String publicId, String tableId, int amount) {
        String pid = requirePublicId(publicId);
        String tid = requireRef(tableId);
        validateAmount(amount);
        if (!walletRepository.debit(pid, amount, TABLE_BUY_IN, "TABLE", tid,
                "BUY_IN:" + tid + ":" + pid)) {
            throw new IllegalStateException("insufficient funds");
        }
    }

    /**
     * リバイのためにウォレットからチップを引き落とします。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @param tableId  テーブル ID
     * @param amount   引き落とし額
     */
    public void rebuy(String publicId, String tableId, int amount) {
        String pid = requirePublicId(publicId);
        String tid = requireRef(tableId);
        validateAmount(amount);
        String key = "REBUY:" + tid + ":" + pid + ":" + Instant.now().toEpochMilli();
        if (!walletRepository.debit(pid, amount, TABLE_REBUY, "TABLE", tid, key)) {
            throw new IllegalStateException("insufficient funds");
        }
    }

    /**
     * テーブルからの持ち帰りチップをウォレットへ戻します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @param tableId  テーブル ID
     * @param amount   入金額
     */
    public void cashOut(String publicId, String tableId, int amount) {
        String pid = requirePublicId(publicId);
        String tid = requireRef(tableId);
        validateAmount(amount);
        walletRepository.credit(pid, amount, TABLE_CASH_OUT, "TABLE", tid,
                "CASH_OUT:" + tid + ":" + pid + ":" + amount);
    }

    /**
     * 日次ボーナスを付与します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     */
    public void claimDailyBonus(String publicId) {
        String pid = requirePublicId(publicId);
        requireCooldownElapsed(pid, DAILY_BONUS, walletProperties.dailyBonusCooldownHours());
        walletRepository.credit(pid, walletProperties.dailyBonusAmount(), DAILY_BONUS, "SYSTEM", pid,
                "DAILY_BONUS:" + pid + ":" + currentWindow(walletProperties.dailyBonusCooldownHours()));
    }

    /**
     * 救済ボーナスを付与します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     */
    public void claimRecovery(String publicId) {
        String pid = requirePublicId(publicId);
        WalletEntry wallet = getBalance(pid);
        if (wallet.chipBalance() > walletProperties.recoveryThreshold()) {
            throw new IllegalStateException("recovery not available");
        }
        requireCooldownElapsed(pid, RECOVERY_BONUS, walletProperties.recoveryCooldownHours());
        walletRepository.credit(pid, walletProperties.recoveryAmount(), RECOVERY_BONUS, "SYSTEM", pid,
                "RECOVERY_BONUS:" + pid + ":" + currentWindow(walletProperties.recoveryCooldownHours()));
    }

    /**
     * 管理者権限で残高を付与します。
     *
     * <p>管理者の認可は {@code adminUsername} で行います（設定リストとの照合）。
     * 対象ユーザーは {@code targetUsername} で検索し、{@code publicId} に変換して入金します。
     *
     * @param adminUsername   管理者ユーザー名（認可チェック用）
     * @param targetUsername  付与対象ユーザー名
     * @param amount          付与額
     */
    public void adminGrant(String adminUsername, String targetUsername, long amount) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("adminUsername is required");
        }
        String normalizedAdmin = adminUsername.trim();
        if (!walletProperties.adminUsernames().contains(normalizedAdmin)) {
            throw new AccessDeniedException("admin access required");
        }
        validateAmount(amount);
        // 対象ユーザーを username → publicId に変換
        String targetPublicId = userRepository.findByUsername(targetUsername)
                .map(User::publicId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetUsername));
        walletRepository.credit(targetPublicId, amount, ADMIN_GRANT, "ADMIN", normalizedAdmin,
                "ADMIN_GRANT:" + normalizedAdmin + ":" + targetPublicId + ":" + Instant.now().toEpochMilli());
    }

    private Instant nextRecoveryAt(String publicId, WalletEntry wallet) {
        if (wallet.chipBalance() > walletProperties.recoveryThreshold()) return null;
        return nextClaimAt(publicId, RECOVERY_BONUS, walletProperties.recoveryCooldownHours());
    }

    private Instant nextClaimAt(String publicId, String reason, int cooldownHours) {
        Instant now = Instant.now();
        return walletRepository.findLastLedgerTime(publicId, reason)
                .map(last -> last.plus(cooldownHours, ChronoUnit.HOURS))
                .filter(next -> next.isAfter(now))
                .orElse(null);
    }

    private void requireCooldownElapsed(String publicId, String reason, int cooldownHours) {
        Instant cutoff = Instant.now().minus(cooldownHours, ChronoUnit.HOURS);
        walletRepository.findLastLedgerTime(publicId, reason)
                .filter(last -> last.isAfter(cutoff))
                .ifPresent(ignored -> { throw new IllegalStateException(reason + " cooldown active"); });
    }

    private long currentWindow(int cooldownHours) {
        long windowMillis = ChronoUnit.HOURS.getDuration().multipliedBy(cooldownHours).toMillis();
        return Instant.now().toEpochMilli() / windowMillis;
    }

    private String requirePublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("publicId is required");
        }
        return publicId.trim();
    }

    private String requireRef(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reference is required");
        }
        return value.trim();
    }

    private void validateAmount(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
    }

    /**
     * 次回請求可能時刻をまとめて返すレコードです。
     *
     * @param nextDailyBonusAt 日次ボーナスの次回請求可能時刻
     * @param nextRecoveryAt   救済ボーナスの次回請求可能時刻
     */
    public record NextClaimTimes(Instant nextDailyBonusAt, Instant nextRecoveryAt) {}
}
