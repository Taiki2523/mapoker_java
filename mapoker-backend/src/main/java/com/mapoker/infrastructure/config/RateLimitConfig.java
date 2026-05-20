package com.mapoker.infrastructure.config;

import com.mapoker.interfaces.http.filter.LoginRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

/**
 * ブルートフォース攻撃対策のレートリミットフィルタを登録するBean設定クラス。
 *
 * <p>{@code local} プロファイルでは無効化される。本番・ステージング環境でのみ適用され、
 * 認証エンドポイント ({@code /v1/auth/google}, {@code /v1/auth/link-google}) への
 * 過剰リクエストを {@link LoginRateLimitFilter} で遮断する。</p>
 */
@Configuration
@Profile("!local")
public class RateLimitConfig {

    /**
     * Google 認証エンドポイント向けのレートリミットフィルタを登録する。
     *
     * @return {@link LoginRateLimitFilter} を認証エンドポイントに適用するBean
     */
    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter() {
        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LoginRateLimitFilter());
        registration.addUrlPatterns("/v1/auth/google", "/v1/auth/link-google");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
