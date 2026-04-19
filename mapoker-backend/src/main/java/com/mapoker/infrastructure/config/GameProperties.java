package com.mapoker.infrastructure.config;

import com.mapoker.domain.game.OddChipRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("game")
public record GameProperties(
        OddChipRule defaultOddChipRule,
        String defaultPlayerName
) {
    public GameProperties {
        if (defaultOddChipRule == null) defaultOddChipRule = OddChipRule.LOW_INDEX;
        if (defaultPlayerName == null || defaultPlayerName.isBlank()) defaultPlayerName = "Player";
    }
}
