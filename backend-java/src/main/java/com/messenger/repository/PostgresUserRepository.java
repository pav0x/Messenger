package com.messenger.repository;

import com.messenger.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

// Реализация UserRepository на PostgreSQL: SQL-запросы через JdbcTemplate.
@Repository
public class PostgresUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;
    private final RowMapper<User> userRowMapper = new UserRowMapper();

    public PostgresUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Вставляет пользователя и возвращает его с присвоенным id.
    @Override
    public User save(String username, String passwordHash, String displayName) {
        if (findByUsername(username).isPresent()) {
            return null; // username уже занят
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        PreparedStatementCreator creator = connection -> {
            // Колонку id указываем явно: с RETURN_GENERATED_KEYS драйвер PostgreSQL возвращает всю строку.
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (username, password_hash, display_name, is_online) VALUES (?, ?, ?, false)",
                    new String[]{"id"});
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, displayName);
            return ps;
        };
        jdbc.update(creator, keyHolder);

        long newId = keyHolder.getKey().longValue();
        return new User(String.valueOf(newId), username, passwordHash, displayName);
    }

    // Ищет пользователя по логину.
    @Override
    public Optional<User> findByUsername(String username) {
        List<User> found = jdbc.query(
                "SELECT * FROM users WHERE username = ?", userRowMapper, username);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(found.get(0));
    }

    // Ищет пользователя по id.
    @Override
    public Optional<User> findById(String id) {
        List<User> found = jdbc.query(
                "SELECT * FROM users WHERE id = ?", userRowMapper, Long.parseLong(id));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(found.get(0));
    }

    // Обновляет онлайн-статус.
    @Override
    public void setOnline(String userId, boolean online) {
        jdbc.update("UPDATE users SET is_online = ? WHERE id = ?", online, Long.parseLong(userId));
    }

    // Преобразует строку результата запроса в User.
    private static final class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User(
                    String.valueOf(rs.getLong("id")),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("display_name")
            );
            user.setOnline(rs.getBoolean("is_online"));
            return user;
        }
    }
}
