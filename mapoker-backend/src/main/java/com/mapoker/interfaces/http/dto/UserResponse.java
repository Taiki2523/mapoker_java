package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.auth.User;

/**
 * ユーザー表示レスポンスです。
 *
 * @param publicId      外部公開用 UUID
 * @param username      表示名
 * @param discriminator 4桁識別子
 * @param displayName   表示名（username#discriminator 形式）
 * @param avatarUrl     アバター画像 URL（null 可）
 * @param newUser       初回登録かどうか（true の場合はプロフィール設定画面へ遷移する）
 */
public record UserResponse(
        @JsonProperty("public_id") String publicId,
        String username,
        String discriminator,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("new_user") boolean newUser
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.publicId(), user.username(), user.discriminator(),
                user.displayName(), user.avatarUrl(), false);
    }

    public static UserResponse fromNew(User user) {
        return new UserResponse(
                user.publicId(), user.username(), user.discriminator(),
                user.displayName(), user.avatarUrl(), true);
    }
}
