package com.mapoker.application;

import java.time.Instant;

/**
 * ウォレット台帳の 1 件を表すレコードです。
 *
 * @param id 台帳 ID
 * @param username ユーザー名
 * @param delta 増減量
 * @param balanceAfter 反映後残高
 * @param reason 取引理由
 * @param referenceType 参照種別
 * @param referenceId 参照 ID
 * @param createdAt 作成日時
 */
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
