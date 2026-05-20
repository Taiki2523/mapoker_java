package com.mapoker.application.auth;

import java.time.LocalDateTime;

/**
 * ユーザーの基本情報を表すレコードです。
 *
 * @param id            DB 内部主キー（外部 API には返さない）
 * @param publicId      外部公開用 UUID
 * @param username      表示名（重複可能）
 * @param discriminator 同名ユーザーを区別する4桁コード
 * @param avatarUrl     アバター画像 URL（任意）
 * @param createdAt     作成日時
 */
public record User(
        long id,
        String publicId,
        String username,
        String discriminator,
        String avatarUrl,
        LocalDateTime createdAt
) {
    /**
     * ゲーム画面等で使用する表示名を返します（例: {@code taiki#1234}）。
     *
     * @return {@code username + "#" + discriminator} の文字列
     */
    public String displayName() {
        return username + "#" + discriminator;
    }
}
