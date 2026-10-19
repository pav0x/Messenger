package com.messenger.exception;

// Пользователь с таким username/id не найден.
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
