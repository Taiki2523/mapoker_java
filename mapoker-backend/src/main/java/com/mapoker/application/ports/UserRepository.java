package com.mapoker.application.ports;

import com.mapoker.application.auth.User;
import java.util.Optional;

/**
 * ユーザー情報の永続化を担当するリポジトリです。
 */
public interface UserRepository {

    /**
     * DB 内部 ID でユーザーを検索します。
     *
     * @param id DB 内部主キー
     * @return 見つかったユーザー
     */
    Optional<User> findById(long id);

    /**
     * 外部公開用 UUID でユーザーを検索します。
     *
     * @param publicId 外部公開用 UUID 文字列
     * @return 見つかったユーザー
     */
    Optional<User> findByPublicId(String publicId);

    /**
     * ユーザー名でユーザーを検索します。同名ユーザーが複数いる場合は最初の1件を返します。
     *
     * @param username ユーザー名
     * @return 見つかったユーザー
     */
    Optional<User> findByUsername(String username);

    /**
     * ユーザー名でパスワードハッシュを検索します。
     *
     * @param username ユーザー名
     * @return 見つかったパスワードハッシュ
     */
    Optional<String> findPasswordHashByUsername(String username);

    /**
     * 指定した {@code username + discriminator} の組み合わせが既に存在するか確認します。
     *
     * @param username      ユーザー名
     * @param discriminator 4桁識別子
     * @return 存在する場合 {@code true}
     */
    boolean existsByUsernameAndDiscriminator(String username, String discriminator);

    /**
     * Google 認証経由でユーザーを新規作成します（パスワードなし）。
     *
     * @param username      表示名
     * @param discriminator 4桁識別子
     * @param avatarUrl     アバター URL（null 可）
     * @return 作成されたユーザー
     */
    User createWithGoogle(String username, String discriminator, String avatarUrl);

    /**
     * ユーザー名を変更します。
     *
     * @param publicId    対象ユーザーの公開 ID
     * @param newUsername 新しいユーザー名
     * @return 更新後のユーザー
     */
    User updateUsername(String publicId, String newUsername);

    /**
     * アバター画像 URL を更新します。
     *
     * @param publicId  対象ユーザーの公開 ID
     * @param avatarUrl 新しいアバター URL
     */
    void updateAvatarUrl(String publicId, String avatarUrl);

    /**
     * パスワードハッシュを更新します。
     *
     * @param username 対象ユーザー名
     * @param newHash  新しいハッシュ値
     */
    void updatePasswordHash(String username, String newHash);
}
