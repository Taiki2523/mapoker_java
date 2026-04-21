package com.mapoker.application;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }
        return userRepository.create(normalizedUsername, passwordEncoder.encode(password));
    }

    public User getByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        return userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalizedUsername));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = normalizeUsername(username);
        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalizedUsername));
        String hash = userRepository.findPasswordHashByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalizedUsername));
        return new org.springframework.security.core.userdetails.User(
                user.username(), hash, List.of());
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        return username.trim();
    }
}
