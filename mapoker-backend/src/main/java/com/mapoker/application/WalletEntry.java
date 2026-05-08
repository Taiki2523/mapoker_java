package com.mapoker.application;

import java.time.Instant;

/**
 * ユーザーウォレット残高を表すレコードです。
 *
 * @param username ユーザー名
 * @param chipBalance チップ残高
 * @param updatedAt 最終更新日時
 */
public record WalletEntry(
        String username,
        long chipBalance,
        Instant updatedAt
) {
}
