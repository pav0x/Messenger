package com.messenger.exception;

// Чата с таким id не существует.
public class ChatNotFoundException extends RuntimeException {
    public ChatNotFoundException(String message) {
        super(message);
    }
}
