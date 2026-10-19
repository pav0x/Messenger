package com.messenger.controller;

import com.messenger.auth.AuthUtils;
import com.messenger.model.User;
import com.messenger.service.ChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// REST: поиск пользователя по логину.
@RestController
@RequestMapping("/api/users")
public class UsersController {

    private final ChatService chatService;

    public UsersController(ChatService chatService) {
        this.chatService = chatService;
    }

    // Данные пользователя для клиента.
    public static class UserView {
        public String id;
        public String display_name;
        public boolean is_online;

        public UserView(User user) {
            this.id = user.getId();
            this.display_name = user.getDisplayName();
            this.is_online = user.isOnline();
        }
    }

    // GET /api/users/lookup?username= — находит пользователя по логину.
    @GetMapping("/lookup")
    public UserView findByUsername(HttpServletRequest request, @RequestParam String username) {
        AuthUtils.requireUserId(request); // только проверка авторизации

        User user = chatService.findUserByUsername(username);
        return new UserView(user);
    }
}
