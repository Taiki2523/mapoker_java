package com.mapoker.infrastructure.persistence;

import com.mapoker.application.auth.User;
import com.mapoker.application.ports.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ローカル開発向けのインメモリ {@link UserRepository} 実装です。
 */
@Repository
@Profile("local")
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, UserRow> byPublicId = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    private record UserRow(
            long id, String publicId, String username, String discriminator,
            String avatarUrl, String passwordHash, LocalDateTime createdAt) {}

    @Override
    public Optional<User> findById(long id) {
        return byPublicId.values().stream()
                .filter(r -> r.id() == id)
                .findFirst()
                .map(this::toUser);
    }

    @Override
    public Optional<User> findByPublicId(String publicId) {
        return Optional.ofNullable(byPublicId.get(publicId)).map(this::toUser);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return byPublicId.values().stream()
                .filter(r -> username.equals(r.username()))
                .findFirst()
                .map(this::toUser);
    }

    @Override
    public Optional<String> findPasswordHashByUsername(String username) {
        return byPublicId.values().stream()
                .filter(r -> username.equals(r.username()))
                .findFirst()
                .map(UserRow::passwordHash);
    }

    @Override
    public boolean existsByUsernameAndDiscriminator(String username, String discriminator) {
        return byPublicId.values().stream()
                .anyMatch(r -> username.equals(r.username()) && discriminator.equals(r.discriminator()));
    }

    @Override
    public User createWithGoogle(String username, String discriminator, String avatarUrl) {
        long id = idSeq.getAndIncrement();
        String publicId = UUID.randomUUID().toString();
        var row = new UserRow(id, publicId, username, discriminator, avatarUrl, null, LocalDateTime.now());
        byPublicId.put(publicId, row);
        return toUser(row);
    }

    @Override
    public void updateAvatarUrl(String publicId, String avatarUrl) {
        UserRow old = byPublicId.get(publicId);
        if (old == null) throw new IllegalArgumentException("User not found: " + publicId);
        byPublicId.put(publicId, new UserRow(old.id(), old.publicId(), old.username(), old.discriminator(),
                avatarUrl, old.passwordHash(), old.createdAt()));
    }

    @Override
    public User updateUsername(String publicId, String newUsername) {
        UserRow old = byPublicId.get(publicId);
        if (old == null) throw new IllegalArgumentException("User not found: " + publicId);
        UserRow updated = new UserRow(old.id(), old.publicId(), newUsername, old.discriminator(),
                old.avatarUrl(), old.passwordHash(), old.createdAt());
        byPublicId.put(publicId, updated);
        return toUser(updated);
    }

    @Override
    public void updatePasswordHash(String username, String newHash) {
        byPublicId.values().stream()
                .filter(r -> username.equals(r.username()))
                .findFirst()
                .ifPresent(old -> byPublicId.put(old.publicId(),
                        new UserRow(old.id(), old.publicId(), old.username(), old.discriminator(),
                                old.avatarUrl(), newHash, old.createdAt())));
    }

    private User toUser(UserRow r) {
        return new User(r.id(), r.publicId(), r.username(), r.discriminator(), r.avatarUrl(), r.createdAt());
    }
}
