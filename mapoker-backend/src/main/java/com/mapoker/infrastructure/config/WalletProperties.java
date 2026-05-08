package com.mapoker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code wallet.*} プレフィックスで管理されるウォレット設定プロパティ。
 *
 * <p>チップ付与額・ボーナスクールダウン・バイインレンジなど、
 * 環境ごとに変更可能なゲーム経済バランスの設定を保持する。
 * デフォルト値はコンパクトコンストラクタで定義されており、
 * 設定ファイルで上書きしない限り適用される。</p>
 *
 * @param initialGrant             新規登録時に付与するチップ数（デフォルト: 10000）
 * @param dailyBonusAmount         デイリーボーナスで付与するチップ数（デフォルト: 1000）
 * @param dailyBonusCooldownHours  デイリーボーナスのクールダウン時間（デフォルト: 24時間）
 * @param recoveryThreshold        残高がこの値以下になったときリカバリーボーナスを受け取れる閾値（デフォルト: 1000）
 * @param recoveryAmount           リカバリーボーナスで付与するチップ数（デフォルト: 5000）
 * @param recoveryCooldownHours    リカバリーボーナスのクールダウン時間（デフォルト: 12時間）
 * @param minBuyinBbMultiplier     最小バイイン額のBB倍率（デフォルト: 20BB）
 * @param maxBuyinBbMultiplier     最大バイイン額のBB倍率（デフォルト: 100BB）
 * @param adminUsernames           管理者権限を持つユーザー名リスト。ブランク・null は除外される
 */
@ConfigurationProperties("wallet")
public record WalletProperties(
        int initialGrant,
        int dailyBonusAmount,
        int dailyBonusCooldownHours,
        int recoveryThreshold,
        int recoveryAmount,
        int recoveryCooldownHours,
        int minBuyinBbMultiplier,
        int maxBuyinBbMultiplier,
        List<String> adminUsernames
) {
    /**
     * コンパクトコンストラクタ。0以下の数値フィールドにデフォルト値を適用し、
     * {@code adminUsernames} からブランク・null エントリを除去してトリムする。
     */
    public WalletProperties {
        if (initialGrant <= 0) initialGrant = 10000;
        if (dailyBonusAmount <= 0) dailyBonusAmount = 1000;
        if (dailyBonusCooldownHours <= 0) dailyBonusCooldownHours = 24;
        if (recoveryThreshold <= 0) recoveryThreshold = 1000;
        if (recoveryAmount <= 0) recoveryAmount = 5000;
        if (recoveryCooldownHours <= 0) recoveryCooldownHours = 12;
        if (minBuyinBbMultiplier <= 0) minBuyinBbMultiplier = 20;
        if (maxBuyinBbMultiplier <= 0) maxBuyinBbMultiplier = 100;
        if (adminUsernames == null) {
            adminUsernames = List.of();
        } else {
            adminUsernames = adminUsernames.stream()
                    .filter(username -> username != null && !username.isBlank())
                    .map(String::trim)
                    .toList();
        }
    }
}
