package com.messenger.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.messenger.auth.SimpleToken;
import com.messenger.model.Message;
import com.messenger.repository.UserRepository;
import com.messenger.service.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;

// WebSocket-обработчик /ws/chat: приём и рассылка сообщений, отметки о прочтении, онлайн-статус.
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String USER_ID_ATTR = "userId";

    private final ChatSessionManager sessions;
    private final ChatService chatService;
    private final UserRepository userRepository;
    private final ObjectMapper json = new ObjectMapper();

    public ChatWebSocketHandler(ChatSessionManager sessions, ChatService chatService,
                                 UserRepository userRepository) {
        this.sessions = sessions;
        this.chatService = chatService;
        this.userRepository = userRepository;
    }

    // Подключение: проверяет токен из адреса ws://host/ws/chat?token=..., иначе закрывает соединение.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String token = extractQueryParam(session.getUri(), "token");
        Optional<String> userId = SimpleToken.verify(token);

        if (userId.isEmpty()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid or expired token"));
            return;
        }

        session.getAttributes().put(USER_ID_ATTR, userId.get());
        sessions.addConnection(userId.get(), session);
        setOnline(userId.get(), true);
    }

    // Отключение: убирает соединение и ставит пользователя офлайн.
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get(USER_ID_ATTR);
        if (userId == null) return; // соединение так и не прошло аутентификацию

        sessions.removeConnection(userId);
        setOnline(userId, false);
    }

    // Входящее сообщение клиента: обычное сообщение или событие {"type": "read"}.
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws IOException {
        String senderId = (String) session.getAttributes().get(USER_ID_ATTR);
        if (senderId == null) return;

        JsonNode payload = json.readTree(textMessage.getPayload());
        String type = payload.hasNonNull("type") ? payload.get("type").asText() : "message";

        if (type.equals("read")) {
            handleReadEvent(senderId, payload);
        } else {
            handleChatMessage(senderId, payload);
        }
    }

    // Сохраняет сообщение {to|chat_id, content, client_id} и рассылает его участникам.
    private void handleChatMessage(String senderId, JsonNode payload) throws IOException {
        if (!payload.hasNonNull("content")) return;
        String content = payload.get("content").asText();
        if (content.isEmpty()) return;

        // client_id возвращается отправителю, чтобы он заменил своё временное сообщение настоящим.
        String clientId = payload.hasNonNull("client_id") ? payload.get("client_id").asText() : null;

        Message saved;
        if (payload.hasNonNull("chat_id")) {
            saved = chatService.sendMessageToChat(senderId, payload.get("chat_id").asText(), content);
        } else if (payload.hasNonNull("to")) {
            saved = chatService.sendMessageToUser(senderId, payload.get("to").asText(), content);
        } else {
            return;
        }

        // Рассылка онлайн-участникам, кроме отправителя.
        List<String> recipientIds = chatService.getOtherMemberIds(saved.getChatId(), senderId);

        boolean deliveredToSomeone = false;
        for (String recipientId : recipientIds) {
            WebSocketSession recipientSession = sessions.getConnection(recipientId);
            if (recipientSession != null && recipientSession.isOpen()) {
                recipientSession.sendMessage(new TextMessage(buildMessageJson(saved, "sent", null)));
                deliveredToSomeone = true;
            }
        }

        if (deliveredToSomeone) {
            chatService.markDelivered(saved.getId());
        }

        // Отправителю возвращается то же сообщение с текущим статусом и client_id.
        WebSocketSession senderSession = sessions.getConnection(senderId);
        if (senderSession != null && senderSession.isOpen()) {
            String status = deliveredToSomeone ? "delivered" : "sent";
            senderSession.sendMessage(new TextMessage(buildMessageJson(saved, status, clientId)));
        }
    }

    // Помечает сообщения чата прочитанными и отправляет остальным участникам read_receipt.
    private void handleReadEvent(String readerId, JsonNode payload) throws IOException {
        if (!payload.hasNonNull("chat_id")) return;
        String chatId = payload.get("chat_id").asText();

        List<String> changedMessageIds = chatService.markChatRead(chatId, readerId);
        if (changedMessageIds.isEmpty()) return;

        ObjectNode receipt = json.createObjectNode();
        receipt.put("type", "read_receipt");
        receipt.put("chat_id", chatId);
        ArrayNode idsArray = receipt.putArray("message_ids");
        for (String messageId : changedMessageIds) {
            idsArray.add(messageId);
        }
        String receiptJson = json.writeValueAsString(receipt);

        // Уведомление онлайн-участникам.
        for (String memberId : chatService.getOtherMemberIds(chatId, readerId)) {
            WebSocketSession memberSession = sessions.getConnection(memberId);
            if (memberSession != null && memberSession.isOpen()) {
                memberSession.sendMessage(new TextMessage(receiptJson));
            }
        }
    }

    // Собирает JSON события "message".
    private String buildMessageJson(Message message, String status, String clientId) throws IOException {
        ObjectNode node = json.createObjectNode();
        node.put("type", "message");
        node.put("chat_id", message.getChatId());
        node.put("message_id", message.getId());
        node.put("from", message.getSenderId());
        node.put("content", message.getContent());
        node.put("created_at", message.getCreatedAt());
        node.put("status", status);
        if (clientId != null) {
            node.put("client_id", clientId);
        }
        return json.writeValueAsString(node);
    }

    // Сохраняет онлайн-статус пользователя в БД.
    private void setOnline(String userId, boolean online) {
        userRepository.setOnline(userId, online);
    }

    // Значение параметра из строки запроса адреса, или null.
    private String extractQueryParam(URI uri, String name) {
        if (uri == null || uri.getQuery() == null) return null;

        for (String pair : uri.getQuery().split("&")) {
            String[] keyAndValue = pair.split("=", 2);
            if (keyAndValue.length == 2 && keyAndValue[0].equals(name)) {
                return keyAndValue[1];
            }
        }
        return null;
    }
}
