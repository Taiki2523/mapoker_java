package com.mapoker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot アプリケーションのエントリポイント。
 *
 * <p>{@code @ConfigurationPropertiesScan} により、{@code CorsProperties} や {@code GameProperties}
 * をはじめとする {@code @ConfigurationProperties} クラスが自動検出される。
 * 新しい設定カテゴリを追加した場合もこのクラスへの追記は不要。
 *
 * <p>認証を無効化したローカル開発モードで起動するには {@code SPRING_PROFILES_ACTIVE=local} を設定する。
 * PostgreSQL を使用する場合は {@code postgresql} プロファイルを追加する。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MapokerApplication {

	/**
	 * アプリケーションを起動する。
	 *
	 * @param args コマンドライン引数（Spring Boot の標準引数形式をサポート）
	 */
	public static void main(String[] args) {
		SpringApplication.run(MapokerApplication.class, args);
	}

}
