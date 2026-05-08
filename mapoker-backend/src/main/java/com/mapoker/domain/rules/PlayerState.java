package com.mapoker.domain.rules;

/**
 * 行動検証に必要なプレイヤー状態を表すレコードです。
 *
 * @param stack 現在スタック
 * @param contributed 現ストリートでの拠出額
 * @param hasFolded フォールド済みかどうか
 * @param isAllIn オールイン状態かどうか
 */
public record PlayerState(int stack, int contributed, boolean hasFolded, boolean isAllIn) {}
