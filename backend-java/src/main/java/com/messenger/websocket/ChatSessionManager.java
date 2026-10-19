package com.messenger.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Активные WebSocket-соединения по userId (кто сейчас онлайн).
@Component
public class ChatSessionManager {

    private final Map<String, WebSocketSession> connections = new ConcurrentHashMap<>();

    // Запоминает соединение пользователя.
    public void addConnection(String userId, WebSocketSession session) {
        connections.put(userId, session);
    }

    // Забывает соединение пользователя.
    public void removeConnection(String userId) {
        connections.remove(userId);
    }

    // Возвращает соединение пользователя, если он онлайн, иначе null.
    public WebSocketSession getConnection(String userId) {
        return connections.get(userId);
    }
}
