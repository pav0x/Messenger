package com.messenger.repository;

import com.messenger.model.Chat;

import java.util.List;
import java.util.Optional;

// Хранилище чатов (личных и групповых) и их участников.
public interface ChatRepository {

    // Личный чат между двумя пользователями: возвращает существующий или создаёт новый.
    Chat findOrCreateDirect(String userAId, String userBId);

    // Создаёт новый групповой чат.
    Chat createGroup(String title, String creatorId, List<String> memberIds);

    // Поиск чата по id.
    Optional<Chat> findById(String chatId);

    // Все чаты пользователя.
    List<Chat> findAllForUser(String userId);

    // Меняет название группы.
    void renameGroup(String chatId, String newTitle);

    // Добавляет участника в чат.
    void addMember(String chatId, String userId);

    // Удаляет участника из чата.
    void removeMember(String chatId, String userId);

    // Удаляет чат целиком; участники и сообщения удаляются каскадом (см. schema.sql).
    void deleteChat(String chatId);
}
