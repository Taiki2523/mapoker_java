package com.mapoker.interfaces.http.dto;

/**
 * API エラーレスポンスです。
 *
 * @param error エラー詳細
 */
public record ErrorResponse(ErrorDetail error) {
    /**
     * エラー詳細 DTO です。
     *
     * @param code エラーコード
     * @param message エラーメッセージ
     */
    public record ErrorDetail(String code, String message) {}

    /**
     * エラーコードとメッセージからレスポンスを生成します。
     *
     * @param code エラーコード
     * @param message エラーメッセージ
     * @return 生成したエラーレスポンス
     */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new ErrorDetail(code, message));
    }
}
