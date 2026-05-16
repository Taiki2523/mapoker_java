package com.mapoker.infrastructure.persistence;

import com.mapoker.application.UserAuthIdentity;
import com.mapoker.application.UserAuthIdentityRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * PostgreSQL への {@link UserAuthIdentityRepository} 実装です。
 */
@Repository
@Profile("postgresql")
public class PostgresUserAuthIdentityRepository implements UserAuthIdentityRepository {

    private final JdbcTemplate jdbc;

    public PostgresUserAuthIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId) {
        var results = jdbc.query(
                "SELECT id, user_id, provider, provider_user_id, created_at" +
                " FROM user_auth_identities WHERE provider = ? AND provider_user_id = ?",
                (rs, n) -> new UserAuthIdentity(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("provider"),
                        rs.getString("provider_user_id"),
                        rs.getTimestamp("created_at").toLocalDateTime()),
                provider, providerUserId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void create(long userId, String provider, String providerUserId) {
        jdbc.update(
                "INSERT INTO user_auth_identities (user_id, provider, provider_user_id) VALUES (?, ?, ?)",
                userId, provider, providerUserId);
    }
}
