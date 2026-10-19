package com.messenger.exception;

// Неверный username или пароль при входе.
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
