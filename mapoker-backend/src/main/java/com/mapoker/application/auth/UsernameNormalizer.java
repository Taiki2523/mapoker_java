package com.mapoker.application.auth;

/**
 * Google アカウント名をアプリ内表示名に正規化するユーティリティです。
 */
public final class UsernameNormalizer {

    private static final int MAX_LENGTH = 50;

    private UsernameNormalizer() {}

    /**
     * 名前を正規化します。
     *
     * <ul>
     *   <li>null / 空白 → {@code "user"}</li>
     *   <li>前後空白を trim</li>
     *   <li>連続空白を1つに圧縮</li>
     *   <li>制御文字を除去</li>
     *   <li>50文字超は切り詰め</li>
     * </ul>
     *
     * @param name 入力文字列
     * @return 正規化済みの名前
     */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) return "user";
        String result = name.trim()
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (result.isBlank()) return "user";
        return result.length() > MAX_LENGTH ? result.substring(0, MAX_LENGTH) : result;
    }
}
