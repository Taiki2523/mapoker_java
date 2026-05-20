package com.mapoker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code google.*} プロパティをバインドする設定レコードです。
 *
 * @param clientId Google OAuth2 クライアント ID。空の場合は Google 認証が無効。
 */
@ConfigurationProperties(prefix = "google")
public record GoogleProperties(String clientId) {}
