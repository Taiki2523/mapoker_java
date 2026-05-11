package com.mapoker.infrastructure.persistence;

import com.mapoker.application.User;
import com.mapoker.application.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring の {@code @Repository} としてローカル実行向けにユーザー情報をメモリ保持する実装です。
 */
@Repository
@Profile("local")
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, UserRow> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    private record UserRow(long id, String username, String passwordHash, LocalDateTime createdAt) {}

    /**
     * 新しいユーザーを作成します。
     *
     * @param username ユーザー名
     * @param passwordHash ハッシュ化済みパスワード
     * @return 作成されたユーザー
     */
    @Override
    public User create(String username, String passwordHash) {
        long id = idSeq.getAndIncrement();
        var row = new UserRow(id, username, passwordHash, LocalDateTime.now());
        store.put(username, row);
        return new User(row.id(), row.username(), row.createdAt());
    }

    /**
     * ユーザー名でユーザーを検索します。
     *
     * @param username ユーザー名
     * @return 見つかったユーザー
     */
    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username))
                .map(r -> new User(r.id(), r.username(), r.createdAt()));
    }

    /**
     * ユーザー名でパスワードハッシュを取得します。
     *
     * @param username ユーザー名
     * @return パスワードハッシュ
     */
    @Override
    public Optional<String> findPasswordHashByUsername(String username) {
        return Optional.ofNullable(store.get(username)).map(UserRow::passwordHash);
    }

    @Override
    public User updateUsername(String currentUsername, String newUsername) {
        UserRow old = store.remove(currentUsername);
        if (old == null) throw new IllegalArgumentException("User not found: " + currentUsername);
        UserRow updated = new UserRow(old.id(), newUsername, old.passwordHash(), old.createdAt());
        store.put(newUsername, updated);
        return new User(updated.id(), updated.username(), updated.createdAt());
    }

    @Override
    public void updatePasswordHash(String username, String newHash) {
        UserRow old = store.get(username);
        if (old == null) throw new IllegalArgumentException("User not found: " + username);
        store.put(username, new UserRow(old.id(), old.username(), newHash, old.createdAt()));
    }
}
