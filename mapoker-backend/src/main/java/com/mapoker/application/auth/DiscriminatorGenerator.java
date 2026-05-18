package com.mapoker.application.auth;

import java.util.Random;

/**
 * {@code 0000}〜{@code 9999} の4桁識別子をランダム生成します。
 *
 * <p>衝突チェックは呼び出し元の Service 層で行います。
 */
public final class DiscriminatorGenerator {

    private static final Random RANDOM = new Random();

    private DiscriminatorGenerator() {}

    /**
     * 4桁の数字文字列を返します（例: "0042"）。
     *
     * @return 4桁の discriminator 文字列
     */
    public static String generate() {
        return String.format("%04d", RANDOM.nextInt(10000));
    }
}
