package com.mapoker.application.wallet;

import com.mapoker.application.auth.User;
import com.mapoker.application.ports.UserRepository;
import com.mapoker.application.ports.WalletRepository;
import com.mapoker.infrastructure.config.WalletProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WalletAuthorizationTest {
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WalletService service = new WalletService(wallets,
            new WalletProperties(10000, 1000, 24, 20, 100, List.of("admin-public-id")), users);

    @Test
    void adminDisplayNameDoesNotGrantAdminPrivileges() {
        assertThatThrownBy(() -> service.adminGrant("admin", "target", 100))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(wallets, users);
    }

    @Test
    void configuredPublicIdCanGrantChips() {
        var target = mock(User.class);
        when(target.publicId()).thenReturn("target-public-id");
        when(users.findByUsername("target")).thenReturn(Optional.of(target));
        service.adminGrant("admin-public-id", "target", 100);
        verify(wallets).credit(eq("target-public-id"), eq(100L), eq("ADMIN_GRANT"),
                eq("ADMIN"), eq("admin-public-id"), anyString());
    }
}
