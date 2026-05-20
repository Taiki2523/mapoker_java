package com.mapoker.application.ports;

import com.mapoker.application.wallet.WalletEntry;
import com.mapoker.application.wallet.WalletLedgerEntry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ウォレット残高と台帳を永続化するリポジトリです。
 *
 * <p>ユーザーの識別には {@code publicId}（UUID）を使用します。
 * {@code username} は一意でないため使用しません。
 */
public interface WalletRepository {

    /**
     * ユーザー用ウォレットを初期化します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     */
    void initializeWallet(String publicId);

    /**
     * 公開 ID からウォレット情報を取得します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @return ウォレット情報
     */
    Optional<WalletEntry> findByPublicId(String publicId);

    /**
     * 指定額を出金します。
     *
     * @param publicId     ユーザーの公開 ID（UUID）
     * @param amount       出金額
     * @param reason       取引理由
     * @param refType      参照種別
     * @param refId        参照 ID
     * @param idempotencyKey 冪等性キー
     * @return 出金に成功した場合は {@code true}
     */
    boolean debit(String publicId, long amount, String reason, String refType, String refId, String idempotencyKey);

    /**
     * 指定額を入金します。
     *
     * @param publicId     ユーザーの公開 ID（UUID）
     * @param amount       入金額
     * @param reason       取引理由
     * @param refType      参照種別
     * @param refId        参照 ID
     * @param idempotencyKey 冪等性キー
     */
    void credit(String publicId, long amount, String reason, String refType, String refId, String idempotencyKey);

    /**
     * 直近の台帳履歴を取得します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @param limit    取得件数の上限
     * @return 台帳履歴一覧
     */
    List<WalletLedgerEntry> findLedger(String publicId, int limit);

    /**
     * 指定理由の最終台帳時刻を取得します。
     *
     * @param publicId ユーザーの公開 ID（UUID）
     * @param reason   取引理由
     * @return 最終台帳時刻
     */
    Optional<Instant> findLastLedgerTime(String publicId, String reason);
}
