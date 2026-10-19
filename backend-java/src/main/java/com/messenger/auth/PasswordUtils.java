package com.messenger.auth;

// Хеширование и проверка паролей (на основе String.hashCode()).
public class PasswordUtils {

    // Превращает пароль в хеш для хранения в БД.
    public static String hashPassword(String plain) {
        return String.valueOf(plain.hashCode());
    }

    // Проверяет пароль по сохранённому хешу.
    public static boolean verifyPassword(String plain, String stored) {
        return hashPassword(plain).equals(stored);
    }
}
