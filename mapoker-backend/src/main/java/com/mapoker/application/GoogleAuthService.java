package com.mapoker.application;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.mapoker.infrastructure.config.GoogleProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Google ID Token の検証とユーザー識別・作成を担当するサービスです。
 */
@Service
public class GoogleAuthService {

    private static final int MAX_DISCRIMINATOR_ATTEMPTS = 20;

    private final String clientId;
    private final UserRepository userRepository;
    private final UserAuthIdentityRepository authIdentityRepository;
    private final ObjectProvider<WalletService> walletServiceProvider;

    public GoogleAuthService(GoogleProperties googleProperties,
                             UserRepository userRepository,
                             UserAuthIdentityRepository authIdentityRepository,
                             ObjectProvider<WalletService> walletServiceProvider) {
        this.clientId = googleProperties.clientId();
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.walletServiceProvider = walletServiceProvider;
    }

    /** ログイン結果を表します。 */
    public record LoginResult(User user, boolean isNewUser) {}

    /**
     * Google ID Token を検証し、対応するユーザーを返します。
     *
     * <p>既存ユーザーはそのまま返し、未登録の場合は新規作成します。
     *
     * @param idTokenString フロントエンドから受け取った ID Token 文字列
     * @return ログイン結果（ユーザー情報と新規登録フラグ）
     * @throws IllegalArgumentException トークンが無効な場合
     * @throws IllegalStateException    Google Client ID が未設定の場合
     */
    @Transactional
    public LoginResult loginWithGoogle(String idTokenString) {
        GoogleIdToken.Payload payload = verifyIdToken(idTokenString);
        String googleSub = payload.getSubject();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        return authIdentityRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, googleSub)
                .map(identity -> new LoginResult(
                        userRepository.findById(identity.userId()).orElseThrow(), false))
                .orElseGet(() -> new LoginResult(createNewUser(googleSub, name, picture), true));
    }

    private User createNewUser(String googleSub, String name, String picture) {
        String username = UsernameNormalizer.normalize(name);

        for (int attempt = 0; attempt < MAX_DISCRIMINATOR_ATTEMPTS; attempt++) {
            String discriminator = DiscriminatorGenerator.generate();
            if (userRepository.existsByUsernameAndDiscriminator(username, discriminator)) {
                continue;
            }
            try {
                User user = userRepository.createWithGoogle(username, discriminator, picture);
                authIdentityRepository.create(user.id(), AuthProvider.GOOGLE, googleSub);
                WalletService walletService = walletServiceProvider.getIfAvailable();
                if (walletService != null) {
                    walletService.initializeWallet(user.username());
                }
                return user;
            } catch (DataIntegrityViolationException e) {
                // 競合時は次の discriminator で再試行
            }
        }
        throw new IllegalStateException(
                "Failed to generate unique discriminator for username: " + username);
    }

    private GoogleIdToken.Payload verifyIdToken(String idTokenString) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("google.client-id is not configured");
        }
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();
            GoogleIdToken token = verifier.verify(idTokenString);
            if (token == null) {
                throw new IllegalArgumentException("Invalid Google ID token");
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Google token verification failed: " + e.getMessage(), e);
        }
    }
}
