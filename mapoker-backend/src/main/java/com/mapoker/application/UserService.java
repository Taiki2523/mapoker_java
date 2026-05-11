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
     * ユーザー名またはパスワードを更新します。
     *
     * <p>{@code newUsername} が非 null の場合はユーザー名を変更します。
     * {@code newPassword} が非 null の場合は {@code currentPassword} を照合してからパスワードを変更します。
     *
     * @param currentUsername 現在のユーザー名
     * @param newUsername     新しいユーザー名（null なら変更なし）
     * @param currentPassword 現在のパスワード（パスワード変更時に必要）
     * @param newPassword     新しいパスワード（null なら変更なし）
     * @return 更新後のユーザー
     * @throws IllegalArgumentException ユーザー名が重複する場合、またはパスワードが不一致の場合
     */
    public User updateProfile(String currentUsername, String newUsername, String currentPassword, String newPassword) {
        String workingUsername = normalizeUsername(currentUsername);

        if (newPassword != null && !newPassword.isBlank()) {
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new IllegalArgumentException("Current password is required to change password");
            }
            String hash = userRepository.findPasswordHashByUsername(workingUsername)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + workingUsername));
            if (!passwordEncoder.matches(currentPassword, hash)) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            userRepository.updatePasswordHash(workingUsername, passwordEncoder.encode(newPassword));
        }

        if (newUsername != null && !newUsername.isBlank()) {
            String normalizedNew = normalizeUsername(newUsername);
            if (!normalizedNew.equals(workingUsername) && userRepository.findByUsername(normalizedNew).isPresent()) {
                throw new IllegalArgumentException("Username already taken");
            }
            User updated = userRepository.updateUsername(workingUsername, normalizedNew);
            workingUsername = normalizedNew;
            return updated;
        }

        return userRepository.findByUsername(workingUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + workingUsername));
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
