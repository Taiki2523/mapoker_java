package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.wallet.WalletLedgerEntry;

import java.time.Instant;

/**
 * ウォレット台帳レスポンスです。
 *
 * @param id 台帳 ID
 * @param delta 増減量
 * @param balanceAfter 反映後残高
 * @param reason 取引理由
 * @param referenceType 参照種別
 * @param referenceId 参照 ID
 * @param createdAt 作成日時
 */
public record WalletLedgerResponse(
        @JsonProperty("id") long id,
        @JsonProperty("delta") long delta,
        @JsonProperty("balance_after") long balanceAfter,
        @JsonProperty("reason") String reason,
        @JsonProperty("reference_type") String referenceType,
        @JsonProperty("reference_id") String referenceId,
        @JsonProperty("created_at") Instant createdAt
) {
    /**
     * 台帳エントリからレスポンスを生成します。
     *
     * @param entry 台帳エントリ
     * @return 生成したレスポンス
     */
    public static WalletLedgerResponse from(WalletLedgerEntry entry) {
        return new WalletLedgerResponse(
                entry.id(),
                entry.delta(),
                entry.balanceAfter(),
                entry.reason(),
                entry.referenceType(),
                entry.referenceId(),
                entry.createdAt());
    }
}
