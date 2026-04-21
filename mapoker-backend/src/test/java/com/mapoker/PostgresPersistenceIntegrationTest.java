package com.mapoker;

import com.mapoker.application.GameService;
import com.mapoker.application.TableService;
import com.mapoker.application.UserService;
import com.mapoker.application.UserTableHistoryService;
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
    private UserService userService;

    @Autowired
    private GameService gameService;

    @Autowired
    private TableService tableService;

    @Autowired
    private UserTableHistoryService userTableHistoryService;

    @Test
    void persistsUsersAndGameLifecycleInPostgres() {
        var user = userService.register("alice", "password123");
        assertThat(user.id()).isPositive();
        assertThat(userService.getByUsername("alice").username()).isEqualTo("alice");

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
        userService.register("history_alice", "password123");

        var created = tableService.createRingTable(new TableService.CreateRingTableInput(
                "History Table",
                3,
                10,
                "public",
                List.of("casual", "newbie")
        ));

        tableService.join(created.table().id(), "history_alice", 1, 0);
        tableService.leave(created.table().id(), "history_alice", 1);

        var history = userTableHistoryService.listRecent("history_alice", 10);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).tableName()).isEqualTo("History Table");
        assertThat(history.get(0).seatIndex()).isEqualTo(1);
        assertThat(history.get(0).flags()).containsExactly("casual", "newbie");
        assertThat(history.get(0).active()).isFalse();
        assertThat(history.get(0).leftAt()).isNotNull();
    }
}
