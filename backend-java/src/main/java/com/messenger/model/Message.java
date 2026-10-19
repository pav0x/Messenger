package com.messenger.model;

// Сообщение в чате: автор, текст, время отправки и статус доставки.
public class Message {

    // SENT — сохранено на сервере; DELIVERED — доставлено онлайн-получателю; READ — прочитано.
    public enum Status { SENT, DELIVERED, READ }

    private final String id;
    private final String chatId;
    private final String senderId;
    private final String content;
    private final String createdAt;
    private Status status;

    public Message(String id, String chatId, String senderId, String content, String createdAt) {
        this.id = id;
        this.chatId = chatId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
        this.status = Status.SENT;
    }

    public String getId() { return id; }
    public String getChatId() { return chatId; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }
    public String getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }

    public void setStatus(Status status) {
        this.status = status;
    }
}
