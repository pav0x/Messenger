package com.messenger.exception;

// Сообщение с таким id не найдено.
public class MessageNotFoundException extends RuntimeException {
    public MessageNotFoundException(String message) {
        super(message);
    }
}
