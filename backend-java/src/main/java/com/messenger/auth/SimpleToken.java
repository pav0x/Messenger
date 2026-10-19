package com.messenger.auth;

import java.time.Instant;
import java.util.Optional;

// Токен авторизации: строка "userId:время_истечения" без подписи.
public class SimpleToken {

    // Создаёт токен со сроком жизни в минутах.
    public static String create(String userId, int lifetimeMinutes) {
        long expiresAtSeconds = Instant.now().plusSeconds(lifetimeMinutes * 60L).getEpochSecond();
        return userId + ":" + expiresAtSeconds;
    }

    // Возвращает userId, если токен корректен и не истёк, иначе empty.
    public static Optional<String> verify(String token) {
        if (token == null) return Optional.empty();

        int separatorIndex = token.indexOf(':');
        if (separatorIndex < 0) return Optional.empty();

        String userId = token.substring(0, separatorIndex);
        String expiresAtText = token.substring(separatorIndex + 1);

        long expiresAtSeconds;
        try {
            expiresAtSeconds = Long.parseLong(expiresAtText);
        } catch (NumberFormatException e) {
            return Optional.empty(); // не число после двоеточия — токен испорчен
        }

        long nowSeconds = Instant.now().getEpochSecond();
        if (nowSeconds > expiresAtSeconds) return Optional.empty(); // токен истёк

        return Optional.of(userId);
    }
}
