package com.mapoker.interfaces.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.validation.ConstraintViolationException;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * GlobalExceptionHandler の単体テスト。
 *
 * <p>Spring を起動せずに各ハンドラメソッドを直接呼び出して検証する。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // -----------------------------------------------------------------------
    // handleNotFound
    // -----------------------------------------------------------------------

    @Test
    void handleNotFoundReturnsNotFoundCode() {
        var response = handler.handleNotFound(new NoSuchElementException("game not found: abc"));

        assertThat(response.error().code()).isEqualTo("not_found");
        assertThat(response.error().message()).isEqualTo("game not found: abc");
    }

    // -----------------------------------------------------------------------
    // handleBadRequest
    // -----------------------------------------------------------------------

    @Test
    void handleBadRequestReturnsInvalidRequestCode() {
        var response = handler.handleBadRequest(new IllegalArgumentException("invalid amount"));

        assertThat(response.error().code()).isEqualTo("invalid_request");
        assertThat(response.error().message()).isEqualTo("invalid amount");
    }

    // -----------------------------------------------------------------------
    // handleInvalidState
    // -----------------------------------------------------------------------

    @Test
    void handleInvalidStateReturnsInvalidActionCode() {
        var response = handler.handleInvalidState(new IllegalStateException("not current player"));

        assertThat(response.error().code()).isEqualTo("invalid_action");
        assertThat(response.error().message()).isEqualTo("not current player");
    }

    // -----------------------------------------------------------------------
    // handleAccessDenied
    // -----------------------------------------------------------------------

    @Test
    void handleAccessDeniedReturnsForbiddenCode() {
        var response = handler.handleAccessDenied(new AccessDeniedException("access denied"));

        assertThat(response.error().code()).isEqualTo("forbidden");
        assertThat(response.error().message()).isEqualTo("access denied");
    }

    // -----------------------------------------------------------------------
    // handleValidation — FieldError あり
    // -----------------------------------------------------------------------

    @Test
    void handleValidationReturnsFieldErrorMessages() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "bigBlind", "must be positive"));
        bindingResult.addError(new FieldError("req", "players", "must not be empty"));
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.error().code()).isEqualTo("invalid_request");
        assertThat(response.error().message())
                .contains("bigBlind: must be positive")
                .contains("players: must not be empty");
    }

    @Test
    void handleValidationUsesFieldNameWhenMessageBlank() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "amount", null, false, null, null, ""));
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.error().code()).isEqualTo("invalid_request");
        assertThat(response.error().message()).contains("amount is invalid");
    }

    @Test
    void handleValidationFallsBackWhenNoFieldErrors() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.error().code()).isEqualTo("invalid_request");
        assertThat(response.error().message()).isEqualTo("request validation failed");
    }

    // -----------------------------------------------------------------------
    // handleConstraintViolation
    // -----------------------------------------------------------------------

    @Test
    void handleConstraintViolationReturnsInvalidRequestCode() {
        var response = handler.handleConstraintViolation(
                new ConstraintViolationException("size must be <= 50", Set.of()));

        assertThat(response.error().code()).isEqualTo("invalid_request");
        assertThat(response.error().message()).contains("size must be <= 50");
    }

    // -----------------------------------------------------------------------
    // handleUnreadableMessage
    // -----------------------------------------------------------------------

    @Test
    void handleUnreadableMessageReturnsMalformedBody() {
        var response = handler.handleUnreadableMessage(
                mock(HttpMessageNotReadableException.class));

        assertThat(response.error().code()).isEqualTo("invalid_request");
        assertThat(response.error().message()).isEqualTo("malformed request body");
    }

    // -----------------------------------------------------------------------
    // handleGeneral
    // -----------------------------------------------------------------------

    @Test
    void handleGeneralReturnsInternalError() {
        var response = handler.handleGeneral(new RuntimeException("unexpected"));

        assertThat(response.error().code()).isEqualTo("internal_error");
        assertThat(response.error().message()).isEqualTo("unexpected");
    }
}
