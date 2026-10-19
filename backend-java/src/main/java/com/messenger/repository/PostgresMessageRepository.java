package com.messenger.repository;

import com.messenger.model.Message;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Реализация MessageRepository на PostgreSQL.
@Repository
public class PostgresMessageRepository implements MessageRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<Message> messageRowMapper = new MessageRowMapper();

    public PostgresMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Вставляет сообщение со статусом SENT и возвращает его с присвоенным id.
    @Override
    public Message save(String chatId, String senderId, String content) {
        long createdAt = Instant.now().getEpochSecond();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        // Колонку id указываем явно (см. PostgresUserRepository.save).
        PreparedStatementCreator creator = connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO messages (chat_id, sender_id, content, status, created_at) " +
                            "VALUES (?, ?, ?, 'SENT', ?)",
                    new String[]{"id"});
            ps.setLong(1, Long.parseLong(chatId));
            ps.setLong(2, Long.parseLong(senderId));
            ps.setString(3, content);
            ps.setLong(4, createdAt);
            return ps;
        };
        jdbc.update(creator, keyHolder);

        long newId = keyHolder.getKey().longValue();
        return new Message(String.valueOf(newId), chatId, senderId, content, String.valueOf(createdAt));
    }

    // Ищет сообщение по id.
    @Override
    public Optional<Message> findById(String messageId) {
        List<Message> found = jdbc.query(
                "SELECT * FROM messages WHERE id = ?", messageRowMapper, Long.parseLong(messageId));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(found.get(0));
    }

    // Все сообщения чата по возрастанию id.
    @Override
    public List<Message> findAllByChatId(String chatId) {
        return jdbc.query(
                "SELECT * FROM messages WHERE chat_id = ? ORDER BY id",
                messageRowMapper, Long.parseLong(chatId));
    }

    // Страница сообщений: выбираем нужный кусок по убыванию id и переворачиваем, чтобы порядок был от старых к новым.
    @Override
    public List<Message> findPage(String chatId, int page, int limit) {
        int offset = page * limit;

        List<Message> newestFirst = jdbc.query(
                "SELECT * FROM messages WHERE chat_id = ? ORDER BY id DESC LIMIT ? OFFSET ?",
                messageRowMapper, Long.parseLong(chatId), limit, offset);

        List<Message> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    // Удаляет сообщение.
    @Override
    public void delete(String messageId) {
        jdbc.update("DELETE FROM messages WHERE id = ?", Long.parseLong(messageId));
    }

    // Обновляет статус сообщения.
    @Override
    public void updateStatus(String messageId, Message.Status status) {
        jdbc.update("UPDATE messages SET status = ? WHERE id = ?",
                status.name(), Long.parseLong(messageId));
    }

    // Преобразует строку результата запроса в Message.
    private static final class MessageRowMapper implements RowMapper<Message> {
        @Override
        public Message mapRow(ResultSet rs, int rowNum) throws SQLException {
            Message message = new Message(
                    String.valueOf(rs.getLong("id")),
                    String.valueOf(rs.getLong("chat_id")),
                    String.valueOf(rs.getLong("sender_id")),
                    rs.getString("content"),
                    String.valueOf(rs.getLong("created_at"))
            );
            message.setStatus(Message.Status.valueOf(rs.getString("status")));
            return message;
        }
    }
}
