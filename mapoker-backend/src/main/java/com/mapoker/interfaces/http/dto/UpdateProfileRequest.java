package com.mapoker.interfaces.http.dto;

import jakarta.validation.constraints.Size;

/**
 * プロフィール更新リクエスト。
 *
 * <p>{@code newUsername} と {@code newPassword} はどちらか一方だけ、あるいは両方を指定できる。
 * {@code newPassword} を指定する場合は {@code currentPassword} が必須。
 *
 * @param newUsername     新しいユーザー名（省略時は変更なし）
 * @param currentPassword 現在のパスワード（パスワード変更時に必須）
 * @param newPassword     新しいパスワード（省略時は変更なし）
 */
public record UpdateProfileRequest(
        @Size(min = 3, max = 50) String newUsername,
        String currentPassword,
        @Size(min = 8, max = 100) String newPassword) {}
