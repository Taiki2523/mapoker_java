package com.mapoker.infrastructure.persistence;

import com.mapoker.application.User;
import com.mapoker.application.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
@Profile("postgresql")
public class PostgresUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    public PostgresUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public User create(String username, String passwordHash) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, password_hash) VALUES (?, ?) RETURNING id, username, created_at",
                (rs, n) -> mapUser(rs),
                username, passwordHash);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        var results = jdbc.query(
                "SELECT id, username, created_at FROM users WHERE username = ?",
                (rs, n) -> mapUser(rs),
                username);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<String> findPasswordHashByUsername(String username) {
        var results = jdbc.query(
                "SELECT password_hash FROM users WHERE username = ?",
                (rs, n) -> rs.getString("password_hash"),
                username);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
