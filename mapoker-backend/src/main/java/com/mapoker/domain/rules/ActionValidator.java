package com.mapoker.domain.rules;

/**
 * テーブル状態に対してプレイヤー行動の妥当性を検証するユーティリティです。
 */
public final class ActionValidator {

    private ActionValidator() {}

    /**
     * 現在状態で指定行動が許可されるかを検証します。
     *
     * @param table テーブル状態
     * @param player プレイヤー状態
     * @param action 検証対象の行動
     * @throws IllegalArgumentException 行動が現在状態に適合しない場合
     */
    public static void validate(TableState table, PlayerState player, Action action) {
        if (player.hasFolded()) throw new IllegalArgumentException("player already folded");
        if (player.isAllIn()) throw new IllegalArgumentException("player is all-in");
        if (player.stack() <= 0) throw new IllegalArgumentException("player has no chips");
        if (action.amount() < 0) throw new IllegalArgumentException("amount must be non-negative");

        int toCall = currentToCall(table, player);
        int maxRaise = player.contributed() + player.stack();

        switch (action.type()) {
            case FOLD -> {}
            case CHECK -> {
                if (toCall != 0)
                    throw new IllegalArgumentException("cannot check: need to call " + toCall);
            }
            case CALL -> {
                if (toCall == 0) throw new IllegalArgumentException("cannot call: nothing to call");
                if (action.amount() == 0) break; // auto-call
                int callAmount = Math.min(toCall, player.stack());
                if (action.amount() != callAmount)
                    throw new IllegalArgumentException("call amount must be " + callAmount);
            }
            case BET -> {
                if (table.currentBet() != 0)
                    throw new IllegalArgumentException("cannot bet: bet already exists");
                if (action.amount() < table.bigBlind())
                    throw new IllegalArgumentException("bet must be at least big blind (" + table.bigBlind() + ")");
                if (action.amount() > player.stack())
                    throw new IllegalArgumentException("bet exceeds stack");
            }
            case RAISE -> {
                if (table.currentBet() == 0)
                    throw new IllegalArgumentException("cannot raise: no bet to raise");
                if (!table.raiseOpen())
                    throw new IllegalArgumentException("cannot raise: betting not reopened");
                if (action.amount() <= table.currentBet())
                    throw new IllegalArgumentException("raise must be above current bet (" + table.currentBet() + ")");
                int raiseSize = action.amount() - table.currentBet();
                int minRaise = minRaiseSize(table);
                if (raiseSize < minRaise)
                    throw new IllegalArgumentException("raise size must be at least " + minRaise);
                if (action.amount() > maxRaise)
                    throw new IllegalArgumentException("raise exceeds stack");
            }
            case ALL_IN -> {
                if (player.stack() <= 0) throw new IllegalArgumentException("cannot all-in: no chips");
            }
            default -> throw new IllegalArgumentException("unknown action type");
        }
    }

    private static int minRaiseSize(TableState table) {
        return Math.max(table.lastRaiseSize(), table.bigBlind());
    }

    private static int currentToCall(TableState table, PlayerState player) {
        int toCall = table.currentBet() - player.contributed();
        return Math.max(toCall, 0);
    }
}
