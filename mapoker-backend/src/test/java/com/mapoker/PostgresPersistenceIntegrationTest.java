package com.mapoker;

import com.mapoker.application.game.GameService;
import com.mapoker.application.table.TableLifecycleService;
import com.mapoker.application.table.TableMembershipService;
import com.mapoker.application.table.TableQueryService;
import com.mapoker.application.ports.UserRepository;
import com.mapoker.application.history.UserTableHistoryService;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.rules.ActionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MapokerApplication.class)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("postgresql")
@Testcontainers(disabledWithoutDocker = true)
class PostgresPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameService gameService;

    @Autowired
    private TableLifecycleService tableLifecycleService;
    @Autowired
    private TableMembershipService tableMembershipService;

    @Autowired
    private UserTableHistoryService userTableHistoryService;

    @Test
    void persistsUsersAndGameLifecycleInPostgres() {
        var user = userRepository.createWithGoogle("alice", "0000", null);
        assertThat(user.id()).isPositive();
        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.publicId()).isNotBlank();

        var game = gameService.createGame(
                List.of(
                        new GameService.PlayerInput("p1", 100),
                        new GameService.PlayerInput("p2", 100)
                ),
                0,
                10,
                42L,
                OddChipRule.LOW_INDEX
        );

        var started = gameService.startHand(game.getId(), 10);
        int actor = started.getCurrentPlayer();
        var nextState = gameService.applyAction(game.getId(), actor, ActionType.CALL, 0);
        var reloaded = gameService.getGame(game.getId());

        assertThat(nextState.getStatus()).isIn(GameStatus.IN_PROGRESS, GameStatus.SHOWDOWN, GameStatus.FINISHED);
        assertThat(reloaded.getPlayers()).hasSize(2);
        assertThat(reloaded.getPlayers()).allSatisfy(player -> assertThat(player.getHole()).hasSize(2));
        assertThat(gameService.getActions(game.getId())).hasSize(1);
    }

    @Test
    void persistsUserTableHistoryInPostgres() {
        userRepository.createWithGoogle("history_alice", "0001", null);

        var created = tableLifecycleService.createRingTable(new TableLifecycleService.CreateRingTableInput(
                "History Table",
                3,
                10,
                "public",
                List.of("casual", "newbie")
        ));

        var joinResult = tableMembershipService.join(created.table().id(), "history_alice", 0);
        tableMembershipService.leave(created.table().id(), "history_alice", null);

        var history = userTableHistoryService.listRecent("history_alice", 10);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).tableName()).isEqualTo("History Table");
        assertThat(history.get(0).seatIndex()).isEqualTo(joinResult.assignedSeatIndex());
        assertThat(history.get(0).flags()).containsExactly("casual", "newbie");
        assertThat(history.get(0).active()).isFalse();
        assertThat(history.get(0).leftAt()).isNotNull();
    }
}
