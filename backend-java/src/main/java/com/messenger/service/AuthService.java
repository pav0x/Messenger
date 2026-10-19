package com.messenger.service;

import com.messenger.auth.PasswordUtils;
import com.messenger.auth.SimpleToken;
import com.messenger.exception.InvalidCredentialsException;
import com.messenger.exception.UsernameTakenException;
import com.messenger.exception.ValidationException;
import com.messenger.model.User;
import com.messenger.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

// Регистрация и вход: проверка данных, хеширование пароля, выдача токена.
@Service
public class AuthService {

    // Срок жизни токена — сутки.
    private static final int TOKEN_LIFETIME_MINUTES = 24 * 60;

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Регистрирует пользователя. Бросает ValidationException или UsernameTakenException.
    public User register(String username, String password, String displayName) {
        if (username == null || password == null) {
            throw new ValidationException("username/password required");
        }
        if (username.length() < 3 || password.length() < 6) {
            throw new ValidationException("username >= 3 chars, password >= 6 chars");
        }

        String finalDisplayName = (displayName != null) ? displayName : username;
        String passwordHash = PasswordUtils.hashPassword(password);
        User user = userRepository.save(username, passwordHash, finalDisplayName);

        if (user == null) {
            throw new UsernameTakenException("username already taken");
        }

        return user;
    }

    // Проверяет логин и пароль. Бросает InvalidCredentialsException при неверных данных.
    public User login(String username, String password) {
        Optional<User> found = userRepository.findByUsername(username);

        if (found.isEmpty()) {
            throw new InvalidCredentialsException("invalid credentials");
        }

        User user = found.get();
        if (!PasswordUtils.verifyPassword(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("invalid credentials");
        }

        return user;
    }

    // Создаёт токен авторизации для пользователя.
    public String createTokenFor(User user) {
        return SimpleToken.create(user.getId(), TOKEN_LIFETIME_MINUTES);
    }
}
