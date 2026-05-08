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
 * 認証エンドポイント ({@code /v1/auth/login}, {@code /v1/auth/register}) への
 * 過剰リクエストを {@link LoginRateLimitFilter} で遮断する。</p>
 */
@Configuration
@Profile("!local")
public class RateLimitConfig {

    /**
     * ログイン・登録エンドポイント向けのレートリミットフィルタを登録する。
     *
     * <p>フィルタは {@link Ordered#HIGHEST_PRECEDENCE} + 10 の優先度で適用されるため、
     * 認証チェックより前にリクエストを遮断できる。</p>
     *
     * @return {@link LoginRateLimitFilter} を認証エンドポイントに適用するBean
     */
    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilter() {
        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LoginRateLimitFilter());
        registration.addUrlPatterns("/v1/auth/login", "/v1/auth/register");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
