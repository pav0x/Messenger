package com.messenger.exception;

// Токен отсутствует, неверный или истёк.
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
