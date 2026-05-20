package com.mapoker.infrastructure.persistence;

import com.mapoker.application.auth.User;
import com.mapoker.application.ports.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * PostgreSQL への {@link UserRepository} 実装です。
 */
@Repository
@Profile("postgresql")
public class PostgresUserRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    public PostgresUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findById(long id) {
        var results = jdbc.query(
                "SELECT id, public_id, username, discriminator, avatar_url, created_at FROM users WHERE id = ?",
                (rs, n) -> mapUser(rs), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<User> findByPublicId(String publicId) {
        var results = jdbc.query(
                "SELECT id, public_id, username, discriminator, avatar_url, created_at FROM users WHERE public_id = ?::uuid",
                (rs, n) -> mapUser(rs), publicId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        var results = jdbc.query(
                "SELECT id, public_id, username, discriminator, avatar_url, created_at FROM users WHERE username = ?",
                (rs, n) -> mapUser(rs), username);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<String> findPasswordHashByUsername(String username) {
        var results = jdbc.query(
                "SELECT password_hash FROM users WHERE username = ?",
                (rs, n) -> rs.getString("password_hash"), username);
        return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.get(0));
    }

    @Override
    public boolean existsByUsernameAndDiscriminator(String username, String discriminator) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ? AND discriminator = ?",
                Integer.class, username, discriminator);
        return count != null && count > 0;
    }

    @Override
    public User createWithGoogle(String username, String discriminator, String avatarUrl) {
        return jdbc.queryForObject(
                "INSERT INTO users (username, discriminator, avatar_url) VALUES (?, ?, ?)" +
                " RETURNING id, public_id, username, discriminator, avatar_url, created_at",
                (rs, n) -> mapUser(rs), username, discriminator, avatarUrl);
    }

    @Override
    public void updateAvatarUrl(String publicId, String avatarUrl) {
        jdbc.update("UPDATE users SET avatar_url = ? WHERE public_id = ?::uuid", avatarUrl, publicId);
    }

    @Override
    public User updateUsername(String publicId, String newUsername) {
        return jdbc.queryForObject(
                "UPDATE users SET username = ? WHERE public_id = ?::uuid" +
                " RETURNING id, public_id, username, discriminator, avatar_url, created_at",
                (rs, n) -> mapUser(rs), newUsername, publicId);
    }

    @Override
    public void updatePasswordHash(String username, String newHash) {
        jdbc.update("UPDATE users SET password_hash = ? WHERE username = ?", newHash, username);
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("public_id"),
                rs.getString("username"),
                rs.getString("discriminator"),
                rs.getString("avatar_url"),
                rs.getTimestamp("created_at").toLocalDateTime());
    }
}
