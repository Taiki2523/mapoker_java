package com.mapoker.application.table;

import com.mapoker.application.game.GameLifecycleService;
import com.mapoker.application.game.GameReadService;
import com.mapoker.application.history.UserTableHistoryService;
import com.mapoker.application.wallet.WalletService;
import com.mapoker.infrastructure.config.GameProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MembershipAuthorizationTest {
    @Test
    @SuppressWarnings("unchecked")
    void authenticatedUserCannotRemoveSomeoneElseByName() {
        var store = new TableStore();
        var victim = new TableMemberRecord("alice", 0, "2026-10-24T00:00:00Z", false,
                "alice", null, "victim-public-id");
        store.getOrInitMembers("table").add(victim);
        var query = mock(TableQueryService.class);
        var table = mock(TableRecord.class);
        when(table.id()).thenReturn("table");
        when(query.getTable("table")).thenReturn(table);
        var games = mock(GameReadService.class);
        ObjectProvider<WalletService> wallets = mock(ObjectProvider.class);
        var service = new TableMembershipService(store, query, mock(TableEventPublisher.class),
                mock(GameLifecycleService.class), games, new GameProperties(null, null),
                wallets, mock(UserTableHistoryService.class));
        assertThat(service.leave("table", "alice", null, "attacker-public-id")).containsExactly(victim);
        assertThat(store.getOrInitMembers("table")).containsExactly(victim);
        verifyNoInteractions(games, wallets);
    }
}
