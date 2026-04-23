package com.mapoker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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
