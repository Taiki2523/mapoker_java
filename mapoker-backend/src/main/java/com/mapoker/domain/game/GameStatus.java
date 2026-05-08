package com.mapoker.domain.game;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * ゲーム進行状態を表す列挙型です。
 */
public enum GameStatus {
    /** ハンド進行中です。 */
    IN_PROGRESS("in_progress"),
    /** ショーダウン処理中です。 */
    SHOWDOWN("showdown"),
    /** ハンドが終了しています。 */
    FINISHED("finished");

    private final String label;

    GameStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
