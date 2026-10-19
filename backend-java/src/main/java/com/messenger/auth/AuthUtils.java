package com.messenger.auth;

import com.messenger.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

// Разбор заголовка Authorization в контроллерах.
public class AuthUtils {

    // Возвращает userId из токена. Бросает UnauthorizedException, если токена нет, он неверный или истёк.
    public static String requireUserId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        String bearerPrefix = "Bearer ";

        if (header == null || !header.startsWith(bearerPrefix)) {
            throw new UnauthorizedException("missing bearer token");
        }

        String token = header.substring(bearerPrefix.length());
        Optional<String> userId = SimpleToken.verify(token);

        if (userId.isEmpty()) {
            throw new UnauthorizedException("invalid or expired token");
        }

        return userId.get();
    }
}
