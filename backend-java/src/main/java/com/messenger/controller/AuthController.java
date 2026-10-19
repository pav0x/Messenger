package com.messenger.controller;

import com.messenger.model.User;
import com.messenger.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// REST: регистрация и вход. При успехе возвращают {token, user_id}.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Тело запроса регистрации/входа (имена полей совпадают с JSON).
    public static class AuthRequest {
        public String username;
        public String password;
        public String display_name;
    }

    // Ответ с токеном.
    public static class AuthResponse {
        public String token;
        public String user_id;

        public AuthResponse(String token, String userId) {
            this.token = token;
            this.user_id = userId;
        }
    }

    // POST /api/auth/register — регистрация.
    @PostMapping("/register")
    public AuthResponse register(@RequestBody AuthRequest body) {
        User user = authService.register(body.username, body.password, body.display_name);
        String token = authService.createTokenFor(user);
        return new AuthResponse(token, user.getId());
    }

    // POST /api/auth/login — вход.
    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest body) {
        User user = authService.login(body.username, body.password);
        String token = authService.createTokenFor(user);
        return new AuthResponse(token, user.getId());
    }
}
