package com.mapoker.interfaces.http;

import com.mapoker.interfaces.http.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Spring の {@code @RestControllerAdvice} として HTTP 例外を API エラー応答へ変換するハンドラです。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 未検出エラーを 404 応答へ変換します。
     *
     * @param e 発生した例外
     * @return エラー応答
     */
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NoSuchElementException e) {
        return ErrorResponse.of("not_found", e.getMessage());
    }

    /**
     * 不正な入力エラーを 400 応答へ変換します。
     *
     * @param e 発生した例外
     * @return エラー応答
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(IllegalArgumentException e) {
        return ErrorResponse.of("invalid_request", e.getMessage());
    }

    /**
     * 状態不整合エラーを 400 応答へ変換します。
     *
     * @param e 発生した例外
     * @return エラー応答
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidState(IllegalStateException e) {
        return ErrorResponse.of("invalid_action", e.getMessage());
    }

    /**
     * 権限不足エラーを 403 応答へ変換します。
     *
     * @param e 発生した例外
     * @return エラー応答
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException e) {
        return ErrorResponse.of("forbidden", e.getMessage());
    }

    /**
     * Bean Validation の入力検証エラーを 400 応答へ変換します。
     *
     * @param e 発生した例外
     * @return エラー応答
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = "request validation failed";
        }
        return ErrorResponse.of("invalid_request", message);
    }

    /**
     * パラメータ制約違反を 400 応答へ変換します。
     *
     * @param e 発生した例外
     * @return エラー応答
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException e) {
        return ErrorResponse.of("invalid_request", e.getMessage());
    }

    /**
     * 読み取り不能なリクエスト本文を 400 応答へ変換します。
     *
     * @param e 発生した例外
     * @return エラー応答
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableMessage(HttpMessageNotReadableException e) {
        return ErrorResponse.of("invalid_request", "malformed request body");
    }

    /**
     * 想定外例外を 500 応答へ変換します。
     *
     * @param e 発生した例外
     * @return エラー応答
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception e) {
        return ErrorResponse.of("internal_error", e.getMessage());
    }

    private String formatFieldError(FieldError error) {
        if (error.getDefaultMessage() == null || error.getDefaultMessage().isBlank()) {
            return error.getField() + " is invalid";
        }
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
