package com.messenger.model;

// Пользователь системы: логин, хеш пароля, отображаемое имя, онлайн-статус.
public class User {
    private final String id;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private boolean online;

    public User(String id, String username, String passwordHash, String displayName) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.online = false;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public boolean isOnline() { return online; }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
