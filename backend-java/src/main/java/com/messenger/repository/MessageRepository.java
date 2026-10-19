package com.messenger.repository;

import com.messenger.model.Message;

import java.util.List;
import java.util.Optional;

// Хранилище сообщений.
public interface MessageRepository {

    // Сохраняет новое сообщение.
    Message save(String chatId, String senderId, String content);

    // Поиск сообщения по id.
    Optional<Message> findById(String messageId);

    // Все сообщения чата без пагинации.
    List<Message> findAllByChatId(String chatId);

    // Страница сообщений: page=0 — самые новые, дальше более старые. Порядок результата — от старых к новым.
    List<Message> findPage(String chatId, int page, int limit);

    // Удаляет сообщение.
    void delete(String messageId);

    // Сохраняет статус сообщения в БД.
    void updateStatus(String messageId, Message.Status status);
}
