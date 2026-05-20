package com.mapoker.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /v1/auth/google} リクエストボディです。
 *
 * @param idToken Google Identity Services から取得した ID Token 文字列
 */
public record GoogleAuthRequest(@NotBlank String idToken) {}
