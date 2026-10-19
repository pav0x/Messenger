package com.messenger.repository;

import com.messenger.model.Chat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Реализация ChatRepository на PostgreSQL.
// Личные чаты ищутся по паре пользователей через direct_chat_pairs, участники всех чатов лежат в chat_members.
@Repository
public class PostgresChatRepository implements ChatRepository {

    private final JdbcTemplate jdbc;

    public PostgresChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Находит личный чат пары или создаёт его вместе с участниками.
    @Override
    @Transactional
    public Chat findOrCreateDirect(String userAId, String userBId) {
        long a = Long.parseLong(userAId);
        long b = Long.parseLong(userBId);
        long smaller = Math.min(a, b);
        long larger = Math.max(a, b);

        List<Long> existingChatIds = jdbc.queryForList(
                "SELECT chat_id FROM direct_chat_pairs WHERE user_a_id = ? AND user_b_id = ?",
                Long.class, smaller, larger);

        if (!existingChatIds.isEmpty()) {
            Optional<Chat> existingChat = findById(String.valueOf(existingChatIds.get(0)));
            if (existingChat.isPresent()) {
                return existingChat.get();
            }
        }

        long chatId = insertChat(false, null, null);
        addMemberRow(chatId, smaller);
        addMemberRow(chatId, larger);
        jdbc.update("INSERT INTO direct_chat_pairs (user_a_id, user_b_id, chat_id) VALUES (?, ?, ?)",
                smaller, larger, chatId);

        return new Chat(String.valueOf(chatId), userAId, userBId);
    }

    // Создаёт группу и добавляет участников.
    @Override
    @Transactional
    public Chat createGroup(String title, String creatorId, List<String> memberIds) {
        long chatId = insertChat(true, title, Long.parseLong(creatorId));
        for (String memberId : memberIds) {
            addMemberRow(chatId, Long.parseLong(memberId));
        }
        return new Chat(String.valueOf(chatId), title, creatorId, memberIds);
    }

    // Загружает чат (тип, название, создателя, участников).
    @Override
    public Optional<Chat> findById(String chatId) {
        long id = Long.parseLong(chatId);

        List<Boolean> groupFlags = jdbc.queryForList(
                "SELECT is_group FROM chats WHERE id = ?", Boolean.class, id);
        if (groupFlags.isEmpty()) {
            return Optional.empty();
        }
        boolean isGroup = groupFlags.get(0);
        List<String> memberIds = findMemberIds(id);

        if (isGroup) {
            List<String> titles = jdbc.queryForList(
                    "SELECT title FROM chats WHERE id = ?", String.class, id);
            List<Long> creatorIds = jdbc.queryForList(
                    "SELECT creator_id FROM chats WHERE id = ?", Long.class, id);
            String creatorId = creatorIds.isEmpty() ? null : idToStringOrNull(creatorIds.get(0));
            return Optional.of(new Chat(chatId, titles.get(0), creatorId, memberIds));
        }
        return Optional.of(new Chat(chatId, memberIds.get(0), memberIds.get(1)));
    }

    // Загружает все чаты пользователя.
    @Override
    public List<Chat> findAllForUser(String userId) {
        List<Long> chatIds = jdbc.queryForList(
                "SELECT chat_id FROM chat_members WHERE user_id = ?", Long.class, Long.parseLong(userId));

        List<Chat> result = new ArrayList<>();
        for (Long chatId : chatIds) {
            Optional<Chat> chat = findById(String.valueOf(chatId));
            if (chat.isPresent()) {
                result.add(chat.get());
            }
        }
        return result;
    }

    // Обновляет название группы.
    @Override
    public void renameGroup(String chatId, String newTitle) {
        jdbc.update("UPDATE chats SET title = ? WHERE id = ?", newTitle, Long.parseLong(chatId));
    }

    // Добавляет участника.
    @Override
    public void addMember(String chatId, String userId) {
        addMemberRow(Long.parseLong(chatId), Long.parseLong(userId));
    }

    // Удаляет участника.
    @Override
    public void removeMember(String chatId, String userId) {
        jdbc.update("DELETE FROM chat_members WHERE chat_id = ? AND user_id = ?",
                Long.parseLong(chatId), Long.parseLong(userId));
    }

    // Удаляет чат (связанные строки удаляются каскадом).
    @Override
    public void deleteChat(String chatId) {
        jdbc.update("DELETE FROM chats WHERE id = ?", Long.parseLong(chatId));
    }

    // Вставляет строку в chats и возвращает id.
    private long insertChat(boolean isGroup, String title, Long creatorId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        // Колонку id указываем явно: с RETURN_GENERATED_KEYS драйвер PostgreSQL возвращает всю строку.
        PreparedStatementCreator creator = connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO chats (is_group, title, creator_id) VALUES (?, ?, ?)",
                    new String[]{"id"});
            ps.setBoolean(1, isGroup);
            ps.setString(2, title);
            if (creatorId != null) {
                ps.setLong(3, creatorId);
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            return ps;
        };
        jdbc.update(creator, keyHolder);
        return keyHolder.getKey().longValue();
    }

    // Добавляет участника; повторное добавление игнорируется (ON CONFLICT DO NOTHING).
    private void addMemberRow(long chatId, long userId) {
        jdbc.update("INSERT INTO chat_members (chat_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                chatId, userId);
    }

    // Id всех участников чата.
    private List<String> findMemberIds(long chatId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT user_id FROM chat_members WHERE chat_id = ?", Long.class, chatId);

        List<String> result = new ArrayList<>();
        for (Long id : ids) {
            result.add(String.valueOf(id));
        }
        return result;
    }

    // Преобразует id в строку, null остаётся null.
    private String idToStringOrNull(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
