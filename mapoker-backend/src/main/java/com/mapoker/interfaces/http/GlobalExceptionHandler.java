package com.mapoker.interfaces.http;

import com.mapoker.interfaces.http.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>4xx（クライアントエラー）は WARN ログ、5xx（サーバーエラー）は ERROR ログ＋スタックトレースを出力します。
 * 500 応答ではユーザーに内部詳細を返さず、汎用メッセージのみを返します。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NoSuchElementException e) {
        log.warn("Not found: {}", e.getMessage());
        return ErrorResponse.of("not_found", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(IllegalArgumentException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ErrorResponse.of("invalid_request", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidState(IllegalStateException e) {
        log.warn("Invalid state: {}", e.getMessage());
        return ErrorResponse.of("invalid_action", e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return ErrorResponse.of("forbidden", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = "request validation failed";
        }
        log.warn("Validation failed: {}", message);
        return ErrorResponse.of("invalid_request", message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        return ErrorResponse.of("invalid_request", e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUnreadableMessage(HttpMessageNotReadableException e) {
        log.warn("Unreadable request body: {}", e.getMessage());
        return ErrorResponse.of("invalid_request", "malformed request body");
    }

    /**
     * 想定外の例外を 500 応答へ変換します。
     * ユーザーには汎用メッセージのみを返し、内部詳細はサーバーログに記録します。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ErrorResponse.of("internal_error", "サーバーエラーが発生しました");
    }

    private String formatFieldError(FieldError error) {
        if (error.getDefaultMessage() == null || error.getDefaultMessage().isBlank()) {
            return error.getField() + " is invalid";
        }
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
