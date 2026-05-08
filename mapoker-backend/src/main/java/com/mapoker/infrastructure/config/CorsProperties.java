package com.mapoker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code cors.*} プレフィックスで管理されるCORS設定プロパティ。
 *
 * <p>{@code application.properties} に以下のキーで設定する。
 * 値が未指定またはnullの場合はコンパクトコンストラクタがデフォルト値で補完する。</p>
 *
 * <pre>
 *   cors.allowed-origin-patterns=https://example.com
 *   cors.allowed-methods=GET,POST
 *   cors.allowed-headers=*
 *   cors.allow-credentials=true
 * </pre>
 *
 * @param allowedOriginPatterns 許可するオリジンのパターン。未指定時は {@code ["*"]}
 * @param allowedMethods        許可するHTTPメソッド。未指定時は {@code ["GET","POST","PUT","DELETE","OPTIONS"]}
 * @param allowedHeaders        許可するリクエストヘッダ。未指定時は {@code ["*"]}
 * @param allowCredentials      認証情報（Cookie/Authorization）の送信を許可するか
 */
@ConfigurationProperties("cors")
public record CorsProperties(
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials
) {
    /**
     * コンパクトコンストラクタ。{@code null} または空リストのフィールドにデフォルト値を適用する。
     */
    public CorsProperties {
        if (allowedOriginPatterns == null || allowedOriginPatterns.isEmpty())
            allowedOriginPatterns = List.of("*");
        if (allowedMethods == null || allowedMethods.isEmpty())
            allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
        if (allowedHeaders == null || allowedHeaders.isEmpty())
            allowedHeaders = List.of("*");
    }
}
