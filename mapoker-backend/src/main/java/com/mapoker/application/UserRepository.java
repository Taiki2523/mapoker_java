package com.mapoker.application;

import java.util.Optional;

public interface UserRepository {
    User create(String username, String passwordHash);
    Optional<User> findByUsername(String username);
    Optional<String> findPasswordHashByUsername(String username);
}
