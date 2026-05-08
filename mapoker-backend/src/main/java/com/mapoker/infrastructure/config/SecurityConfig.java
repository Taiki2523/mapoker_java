package com.mapoker.infrastructure.config;

import com.mapoker.application.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security の全体的なセキュリティ設定クラス。
 *
 * <p>プロファイルによって動作を切り替える：</p>
 * <ul>
 *   <li>{@code local} プロファイル: CSRF無効・全リクエスト許可（開発専用）</li>
 *   <li>その他: セッション認証・CSRF無効・エンドポイント別アクセス制御</li>
 * </ul>
 *
 * <p>CORS設定は {@link CorsProperties} から取得し、全プロファイルで共有する。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;

    /**
     * @param corsProperties {@code cors.*} プロパティから注入されるCORS設定
     */
    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * パスワードのハッシュ化に使用する {@link BCryptPasswordEncoder} を提供する。
     *
     * @return BCryptベースのパスワードエンコーダー
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * {@link UserService} をUserDetailsServiceとして使用する認証プロバイダを構成する。
     *
     * @param userService Spring Security の {@code UserDetailsService} として機能するサービス
     * @return 設定済みの {@link DaoAuthenticationProvider}
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserService userService) {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * {@link AuthenticationManager} をBeanとして公開する。
     * コントローラからのプログラム的認証（ログイン処理）で使用する。
     *
     * @param config Spring Security の認証設定
     * @return {@link AuthenticationManager}
     * @throws Exception 設定取得に失敗した場合
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * {@code local} プロファイル専用のセキュリティフィルタチェーン。
     *
     * <p>CSRF無効化・全リクエスト許可の設定で、{@code curl} による開発時の動作確認を容易にする。
     * 本番環境では絶対に使用しないこと。</p>
     *
     * @param http HttpSecurity ビルダー
     * @return 全許可のセキュリティフィルタチェーン
     * @throws Exception フィルタチェーン構築に失敗した場合
     */
    @Bean
    @Profile("local")
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * 本番・ステージング向けのデフォルトセキュリティフィルタチェーン。
     *
     * <p>セッション固定攻撃対策（migrateSession）を有効化し、認証不要なエンドポイントを
     * {@code /actuator/health} と認証エンドポイントのみに限定する。</p>
     *
     * @param http HttpSecurity ビルダー
     * @return 認証必須のセキュリティフィルタチェーン
     * @throws Exception フィルタチェーン構築に失敗した場合
     */
    @Bean
    @Profile("!local")
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation().migrateSession())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v1/auth/login", "/v1/auth/register").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }

    /**
     * {@link CorsProperties} の設定値を元に全パス向けのCORS設定ソースを生成する。
     *
     * <p>設定値は {@code application.properties} の {@code cors.*} キーから注入される。
     * ワイルドカードオリジン（{@code "*"}）と {@code allowCredentials=true} は
     * ブラウザの仕様上組み合わせ不可のため、本番では具体的なオリジンパターンを設定すること。</p>
     *
     * @return 全パス ({@code /**}) に適用されるCORS設定ソース
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(corsProperties.allowedOriginPatterns());
        config.setAllowedMethods(corsProperties.allowedMethods());
        config.setAllowedHeaders(corsProperties.allowedHeaders());
        config.setAllowCredentials(corsProperties.allowCredentials());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
