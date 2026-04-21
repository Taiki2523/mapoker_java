package com.mapoker.interfaces.http;

import com.mapoker.application.GameService;
import com.mapoker.application.TableService;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.game.Player;
import com.mapoker.infrastructure.config.GameProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameControllerVisibilityTest {

    @Test
    void authenticatedUserOnlySeesOwnHoleCards() {
        GameService gameService = mock(GameService.class);
        TableService tableService = mock(TableService.class);
        GameController controller = new GameController(gameService, new GameProperties(OddChipRule.LOW_INDEX, "Player"), tableService);

        GameState state = startedGame();
        when(gameService.getGame("game-1")).thenReturn(state);
        when(tableService.findSeatIndex("game-1", "alice")).thenReturn(1);

        var response = controller.getGame(
                "game-1",
                0,
                "0",
                new User("alice", "secret", List.of())
        );

        assertThat(response.players().get(0).hole()).isNull();
        assertThat(response.players().get(1).hole()).hasSize(2);
    }

    @Test
    void authenticatedUserCannotSpoofViewerIndexWithoutSeat() {
        GameService gameService = mock(GameService.class);
        TableService tableService = mock(TableService.class);
        GameController controller = new GameController(gameService, new GameProperties(OddChipRule.LOW_INDEX, "Player"), tableService);

        GameState state = startedGame();
        when(gameService.getGame("game-1")).thenReturn(state);
        when(tableService.findSeatIndex("game-1", "mallory")).thenReturn(null);

        var response = controller.getGame(
                "game-1",
                0,
                "0",
                new User("mallory", "secret", List.of())
        );

        assertThat(response.players()).allSatisfy(player -> assertThat(player.hole()).isNull());
    }

    @Test
    void anonymousViewerCanUseExplicitViewerIndex() {
        GameService gameService = mock(GameService.class);
        TableService tableService = mock(TableService.class);
        GameController controller = new GameController(gameService, new GameProperties(OddChipRule.LOW_INDEX, "Player"), tableService);

        GameState state = startedGame();
        when(gameService.getGame("game-1")).thenReturn(state);

        var response = controller.getGame("game-1", 0, "0", null);

        assertThat(response.players().get(0).hole()).hasSize(2);
        assertThat(response.players().get(1).hole()).isNull();
    }

    private static GameState startedGame() {
        GameState game = GameState.newGame(
                List.of(new Player("p1", 100), new Player("p2", 100)),
                0,
                10,
                new Random(42),
                OddChipRule.LOW_INDEX
        );
        game.setId("game-1");
        game.startHand(10);
        return game;
    }
}
