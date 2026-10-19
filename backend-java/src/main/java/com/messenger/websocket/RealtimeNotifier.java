package com.messenger.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

// Отправка WebSocket-событий пользователям. Используется обработчиком WebSocket и REST-контроллерами.
@Component
public class RealtimeNotifier {

    private final ChatSessionManager sessions;
    private final ObjectMapper json = new ObjectMapper();

    public RealtimeNotifier(ChatSessionManager sessions) {
        this.sessions = sessions;
    }

    // Создаёт JSON-событие с заданным типом.
    public ObjectNode newEvent(String type) {
        ObjectNode node = json.createObjectNode();
        node.put("type", type);
        return node;
    }

    // Отправляет событие пользователю, если он онлайн.
    public void sendToUser(String userId, ObjectNode payload) {
        WebSocketSession session = sessions.getConnection(userId);
        if (session == null || !session.isOpen()) {
            return; // пользователь офлайн
        }
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(payload)));
        } catch (IOException e) {
            // Ошибка отправки игнорируется: клиент мог отключиться.
        }
    }

    // Отправляет событие нескольким пользователям.
    public void sendToUsers(List<String> userIds, ObjectNode payload) {
        for (String userId : userIds) {
            sendToUser(userId, payload);
        }
    }
}
