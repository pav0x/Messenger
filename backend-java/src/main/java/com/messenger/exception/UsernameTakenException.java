package com.messenger.exception;

// При регистрации username уже занят кем-то другим.
public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException(String message) {
        super(message);
    }
}
