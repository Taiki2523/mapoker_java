package com.mapoker.application.auth;
import com.mapoker.application.ports.UserRepository;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * ユーザー管理を担当するサービスです。
 *
 * <p>Google 認証によるログイン・新規作成は {@link GoogleAuthService} が担当します。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * public_id でユーザーを取得します。
     *
     * @param publicId 外部公開用 UUID
     * @return 対応するユーザー
     * @throws UsernameNotFoundException 存在しない場合
     */
    public User getByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + publicId));
    }

    /**
     * ユーザー名を変更します。
     *
     * <p>discriminator は変更しません。新しい {@code username + discriminator} が既に存在する場合は例外を投げます。
     *
     * @param publicId    変更対象ユーザーの public_id
     * @param newUsername 新しい表示名
     * @return 更新後のユーザー
     * @throws IllegalArgumentException 新しいユーザー名が空、または username+discriminator が重複する場合
     */
    public User updateUsername(String publicId, String newUsername) {
        String normalized = UsernameNormalizer.normalize(newUsername);
        User current = getByPublicId(publicId);
        if (normalized.equals(current.username())) return current;
        if (userRepository.existsByUsernameAndDiscriminator(normalized, current.discriminator())) {
            throw new IllegalArgumentException("Username and discriminator already taken");
        }
        return userRepository.updateUsername(publicId, normalized);
    }

    /**
     * 既存パスワードユーザーのパスワードを検証します（link-google 移行用）。
     *
     * @param username        ユーザー名
     * @param currentPassword 検証するパスワード
     * @return パスワードが正しければ true
     */
    public boolean verifyPassword(String username, String currentPassword) {
        return userRepository.findPasswordHashByUsername(username)
                .map(hash -> passwordEncoder.matches(currentPassword, hash))
                .orElse(false);
    }
}
