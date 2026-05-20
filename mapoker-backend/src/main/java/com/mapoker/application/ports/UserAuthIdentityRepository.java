package com.mapoker.application.ports;

import com.mapoker.application.auth.UserAuthIdentity;

import java.util.Optional;

/**
 * 外部認証プロバイダーとユーザーの紐付けを管理するリポジトリです。
 */
public interface UserAuthIdentityRepository {

    /**
     * プロバイダーとプロバイダー側ユーザー ID の組み合わせで認証情報を検索します。
     *
     * @param provider       認証プロバイダー識別子（例: "google"）
     * @param providerUserId プロバイダー側のユーザー ID（Google の sub クレーム等）
     * @return 見つかった認証情報
     */
    Optional<UserAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    /**
     * 認証情報を新規登録します。
     *
     * @param userId         ユーザーの DB 内部 ID
     * @param provider       認証プロバイダー識別子
     * @param providerUserId プロバイダー側のユーザー ID
     */
    void create(long userId, String provider, String providerUserId);
}
