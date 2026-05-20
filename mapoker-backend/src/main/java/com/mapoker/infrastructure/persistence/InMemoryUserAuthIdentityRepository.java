package com.mapoker.infrastructure.persistence;

import com.mapoker.application.auth.UserAuthIdentity;
import com.mapoker.application.ports.UserAuthIdentityRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ローカル開発向けのインメモリ {@link UserAuthIdentityRepository} 実装です。
 */
@Repository
@Profile("local")
public class InMemoryUserAuthIdentityRepository implements UserAuthIdentityRepository {

    private final Map<String, UserAuthIdentity> store = new ConcurrentHashMap<>();
    private final AtomicLong idSeq = new AtomicLong(1);

    @Override
    public Optional<UserAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId) {
        String key = provider + ":" + providerUserId;
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void create(long userId, String provider, String providerUserId) {
        String key = provider + ":" + providerUserId;
        store.put(key, new UserAuthIdentity(idSeq.getAndIncrement(), userId, provider, providerUserId, LocalDateTime.now()));
    }
}
