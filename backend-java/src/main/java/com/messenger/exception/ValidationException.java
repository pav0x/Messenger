package com.messenger.exception;

// Данные запроса не прошли проверку (например, короткий пароль).
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
