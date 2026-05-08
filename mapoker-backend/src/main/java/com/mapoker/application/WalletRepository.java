package com.mapoker.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ウォレット残高と台帳を永続化するリポジトリです。
 */
public interface WalletRepository {

    /**
     * ユーザー用ウォレットを初期化します。
     *
     * @param username ユーザー名
     */
    void initializeWallet(String username);

    /**
     * ユーザー名からウォレット情報を取得します。
     *
     * @param username ユーザー名
     * @return ウォレット情報
     */
    Optional<WalletEntry> findByUsername(String username);

    /**
     * 指定額を出金します。
     *
     * @param username ユーザー名
     * @param amount 出金額
     * @param reason 取引理由
     * @param refType 参照種別
     * @param refId 参照 ID
     * @param idempotencyKey 冪等性キー
     * @return 出金に成功した場合は {@code true}
     */
    boolean debit(String username, long amount, String reason, String refType, String refId, String idempotencyKey);

    /**
     * 指定額を入金します。
     *
     * @param username ユーザー名
     * @param amount 入金額
     * @param reason 取引理由
     * @param refType 参照種別
     * @param refId 参照 ID
     * @param idempotencyKey 冪等性キー
     */
    void credit(String username, long amount, String reason, String refType, String refId, String idempotencyKey);

    /**
     * 直近の台帳履歴を取得します。
     *
     * @param username ユーザー名
     * @param limit 取得件数の上限
     * @return 台帳履歴一覧
     */
    List<WalletLedgerEntry> findLedger(String username, int limit);

    /**
     * 指定理由の最終台帳時刻を取得します。
     *
     * @param username ユーザー名
     * @param reason 取引理由
     * @return 最終台帳時刻
     */
    Optional<Instant> findLastLedgerTime(String username, String reason);
}
