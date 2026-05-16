package com.mapoker.interfaces.http.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /v1/auth/me} リクエストボディです。
 *
 * @param newUsername 新しいユーザー名
 */
public record UpdateUsernameRequest(
        @NotBlank @Size(min = 1, max = 50) String newUsername
) {}
