package com.mapoker.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring の {@code @Service} としてユーザー登録と認証用ユーザー解決を担当するサービスです。
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<WalletService> walletServiceProvider;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ObjectProvider<WalletService> walletServiceProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletServiceProvider = walletServiceProvider;
    }

    /**
     * 新しいユーザーを登録します。
     *
     * @param username ユーザー名
     * @param password 平文パスワード
     * @return 作成されたユーザー
     * @throws IllegalArgumentException ユーザー名が既に使用されている場合
     */
    public User register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new IllegalArgumentException("Username already taken");
        }
        User createdUser = userRepository.create(normalizedUsername, passwordEncoder.encode(password));
        WalletService walletService = walletServiceProvider.getIfAvailable();
        if (walletService != null) {
            walletService.initializeWallet(normalizedUsername);
        }
        return createdUser;
    }

    /**
     * ユーザー名からユーザー情報を取得します。
     *
     * @param username ユーザー名
     * @return 対応するユーザー
     * @throws UsernameNotFoundException ユーザーが存在しない場合
     */
    public User getByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        return userRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalizedUsername));
    }

    /**
     * 認証処理用にユーザー詳細を読み込みます。
     *
     * @param username ユーザー名
     * @return 認証に利用するユーザー詳細
     * @throws UsernameNotFoundException ユーザーが存在しない場合
     */
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
