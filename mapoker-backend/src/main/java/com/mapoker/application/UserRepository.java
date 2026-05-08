package com.mapoker.application;

import java.util.Optional;

/**
 * ユーザー情報の永続化を担当するリポジトリです。
 */
public interface UserRepository {

    /**
     * 新しいユーザーを作成します。
     *
     * @param username ユーザー名
     * @param passwordHash ハッシュ化済みパスワード
     * @return 作成されたユーザー
     */
    User create(String username, String passwordHash);

    /**
     * ユーザー名でユーザーを検索します。
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
}
