package com.messenger.exception;

// Пользователь авторизован, но не имеет права на это действие — например,
// пытается прочитать историю чата, в котором не состоит.
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
