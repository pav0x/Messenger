package com.messenger.repository;

import com.messenger.model.User;

import java.util.Optional;

// Хранилище пользователей.
public interface UserRepository {

    // Создаёт пользователя. Возвращает null, если username уже занят.
    User save(String username, String passwordHash, String displayName);

    // Поиск по логину.
    Optional<User> findByUsername(String username);

    // Поиск по id.
    Optional<User> findById(String id);

    // Сохраняет онлайн-статус в БД (изменение поля у объекта в памяти само не сохраняется).
    void setOnline(String userId, boolean online);
}
