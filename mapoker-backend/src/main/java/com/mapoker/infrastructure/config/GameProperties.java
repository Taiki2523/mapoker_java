package com.mapoker.infrastructure.config;

import com.mapoker.domain.game.OddChipRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code game.*} プレフィックスで管理されるゲームデフォルト設定プロパティ。
 *
 * <p>ポーカールール固有の定数は {@link com.mapoker.domain.PokerConstants} に定義し、
 * 環境依存のデフォルト値のみここで管理する。</p>
 *
 * <pre>
 *   game.default-odd-chip-rule=low_index
 *   game.default-player-name=Player
 * </pre>
 *
 * @param defaultOddChipRule 端数チップの分配ルール。未指定時は {@link OddChipRule#LOW_INDEX}
 * @param defaultPlayerName  プレイヤー名が未設定の場合に使う表示名。未指定時は {@code "Player"}
 */
@ConfigurationProperties("game")
public record GameProperties(
        OddChipRule defaultOddChipRule,
        String defaultPlayerName
) {
    /**
     * コンパクトコンストラクタ。{@code null} または空白のフィールドにデフォルト値を適用する。
     */
    public GameProperties {
        if (defaultOddChipRule == null) defaultOddChipRule = OddChipRule.LOW_INDEX;
        if (defaultPlayerName == null || defaultPlayerName.isBlank()) defaultPlayerName = "Player";
    }
}
