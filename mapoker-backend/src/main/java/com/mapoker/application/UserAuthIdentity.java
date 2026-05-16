package com.mapoker.application;

import java.time.LocalDateTime;

/**
 * 外部認証プロバイダーとユーザーの紐付けを表すレコードです。
 *
 * @param id             DB 内部主キー
 * @param userId         ユーザー ID（users.id）
 * @param provider       認証プロバイダー識別子（例: "google"）
 * @param providerUserId プロバイダー側のユーザー ID（Google の sub クレーム等）
 * @param createdAt      作成日時
 */
public record UserAuthIdentity(
        long id,
        long userId,
        String provider,
        String providerUserId,
        LocalDateTime createdAt
) {}
