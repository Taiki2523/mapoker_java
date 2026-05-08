package com.mapoker.domain.rules;

/**
 * 行動検証に必要なテーブル状態を表すレコードです。
 *
 * @param street 現在のストリート
 * @param bigBlind ビッグブラインド額
 * @param currentBet 現在のベット額
 * @param lastRaiseSize 直近のレイズ幅
 * @param raiseOpen レイズが再オープンされているかどうか
 */
public record TableState(Street street, int bigBlind, int currentBet, int lastRaiseSize, boolean raiseOpen) {}
