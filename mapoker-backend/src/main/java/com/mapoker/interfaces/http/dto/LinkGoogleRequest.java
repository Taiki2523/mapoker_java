package com.mapoker.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /v1/auth/link-google} リクエストボディです。
 *
 * <p>パスワード認証から Google 認証へ移行する際に使用します。
 *
 * @param idToken  Google ID Token 文字列
 * @param username 既存ユーザー名
 * @param password 既存パスワード
 */
public record LinkGoogleRequest(
        @NotBlank String idToken,
        @NotBlank String username,
        @NotBlank String password
) {}
