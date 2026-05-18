package com.mapoker.interfaces.http;

import com.mapoker.application.game.ActionRecord;
import com.mapoker.application.game.GameService;
import com.mapoker.application.table.TableLifecycleService;
import com.mapoker.application.table.TableMembershipService;
import com.mapoker.application.table.TableQueryService;
import com.mapoker.application.auth.UserService;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.game.Player;
import com.mapoker.domain.rules.ActionType;
import com.mapoker.infrastructure.config.GameProperties;
import com.mapoker.interfaces.http.dto.ApplyActionRequest;
import com.mapoker.interfaces.http.dto.CreateGameRequest;
import com.mapoker.interfaces.http.dto.StartHandRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GameController の単体テスト。
 *
 * <p>GameService・TableService を Mockito でスタブし、Spring を起動せずに検証する。
 * ホールカード可視性の詳細は GameControllerVisibilityTest を参照。
 */
class GameControllerTest {

    private GameService gameService;
    private TableQueryService tableQueryService;
    private TableLifecycleService tableLifecycleService;
    private UserService userService;
    private GameController controller;

    private static final GameProperties GAME_PROPS =
            new GameProperties(OddChipRule.LOW_INDEX, "Player");

    @BeforeEach
    void setUp() {
        gameService = mock(GameService.class);
        tableQueryService = mock(TableQueryService.class);
        tableLifecycleService = mock(TableLifecycleService.class);
        userService = mock(UserService.class);
        controller = new GameController(gameService, GAME_PROPS, tableQueryService, tableLifecycleService, userService);
    }

    // -----------------------------------------------------------------------
    // createGame
    // -----------------------------------------------------------------------

    @Test
    void createGameReturnsGameResponse() {
        GameState state = newGame();
        when(gameService.createGame(any(), anyInt(), anyInt(), any(), any())).thenReturn(state);

        var req = new CreateGameRequest(
                List.of(new CreateGameRequest.PlayerDto("alice", 1000),
                        new CreateGameRequest.PlayerDto("bob", 1000)),
                0, 10, null, null);

        var response = controller.createGame(req);

        assertThat(response.id()).isEqualTo(state.getId());
        assertThat(response.players()).hasSize(2);
    }

    @Test
    void createGameUsesDefaultOddChipRuleWhenNull() {
        GameState state = newGame();
        when(gameService.createGame(any(), anyInt(), anyInt(), any(), eq(OddChipRule.LOW_INDEX)))
                .thenReturn(state);

        var req = new CreateGameRequest(
                List.of(new CreateGameRequest.PlayerDto("p1", 500)), 0, 10, null, null);

        controller.createGame(req);

        verify(gameService).createGame(any(), anyInt(), anyInt(), any(), eq(OddChipRule.LOW_INDEX));
    }

    @Test
    void createGameForwardsExplicitOddChipRule() {
        GameState state = newGame();
        when(gameService.createGame(any(), anyInt(), anyInt(), any(), eq(OddChipRule.BUTTON_LEFT)))
                .thenReturn(state);

        var req = new CreateGameRequest(
                List.of(new CreateGameRequest.PlayerDto("p1", 500)), 0, 10, null, OddChipRule.BUTTON_LEFT);

        controller.createGame(req);

        verify(gameService).createGame(any(), anyInt(), anyInt(), any(), eq(OddChipRule.BUTTON_LEFT));
    }

    // -----------------------------------------------------------------------
    // listGames
    // -----------------------------------------------------------------------

    @Test
    void listGamesReturnsAllGames() {
        when(gameService.listGames()).thenReturn(List.of(newGame(), newGame()));

        var response = controller.listGames();

        assertThat(response).hasSize(2);
    }

    @Test
    void listGamesHidesHoleCards() {
        GameState started = startedGame();
        when(gameService.listGames()).thenReturn(List.of(started));

        var response = controller.listGames();

        response.get(0).players().forEach(p -> assertThat(p.hole()).isNull());
    }

    @Test
    void listGamesReturnsEmptyList() {
        when(gameService.listGames()).thenReturn(List.of());

        assertThat(controller.listGames()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // getGame — spectator モード
    // -----------------------------------------------------------------------

    @Test
    void getGameSpectatorFlagOneHidesHoleCards() {
        GameState started = startedGame();
        when(gameService.getGame("g1")).thenReturn(started);

        var response = controller.getGame("g1", 0, "1", null);

        response.players().forEach(p -> assertThat(p.hole()).isNull());
    }

    @Test
    void getGameSpectatorFlagTrueHidesHoleCards() {
        GameState started = startedGame();
        when(gameService.getGame("g1")).thenReturn(started);

        var response = controller.getGame("g1", 0, "true", null);

        response.players().forEach(p -> assertThat(p.hole()).isNull());
    }

    // -----------------------------------------------------------------------
    // startHand
    // -----------------------------------------------------------------------

    @Test
    void startHandDelegatesToTableService() {
        GameState started = startedGame();
        when(tableLifecycleService.startHand("g1", 10, false)).thenReturn(started);

        var response = controller.startHand("g1", new StartHandRequest(10));

        assertThat(response.id()).isEqualTo(started.getId());
        verify(tableLifecycleService).startHand("g1", 10, false);
    }

    @Test
    void startHandResponseHidesHoleCards() {
        GameState started = startedGame();
        when(tableLifecycleService.startHand("g1", 10, false)).thenReturn(started);

        var response = controller.startHand("g1", new StartHandRequest(10));

        response.players().forEach(p -> assertThat(p.hole()).isNull());
    }

    // -----------------------------------------------------------------------
    // applyAction
    // -----------------------------------------------------------------------

    @Test
    void applyActionDelegatesToGameService() {
        GameState state = startedGame();
        when(gameService.applyAction("g1", 0, ActionType.FOLD, 0)).thenReturn(state);

        var req = new ApplyActionRequest(0, new ApplyActionRequest.ActionDto(ActionType.FOLD, 0));
        controller.applyAction("g1", req, null, null);

        verify(gameService).applyAction("g1", 0, ActionType.FOLD, 0);
    }

    @Test
    void applyActionReturnsGameResponse() {
        GameState state = startedGame();
        when(gameService.applyAction(anyString(), anyInt(), any(), anyInt())).thenReturn(state);

        var req = new ApplyActionRequest(1, new ApplyActionRequest.ActionDto(ActionType.CALL, 0));
        var response = controller.applyAction("g1", req, null, null);

        assertThat(response.id()).isEqualTo(state.getId());
    }

    @Test
    void applyActionResolvesViewerIndexForAuthenticatedUser() {
        GameState state = startedGame();
        when(gameService.applyAction(anyString(), anyInt(), any(), anyInt())).thenReturn(state);
        var appAlice = new com.mapoker.application.auth.User(
                1L, "pub-alice", "alice", "0000", null, java.time.LocalDateTime.now());
        when(userService.getByPublicId("pub-alice")).thenReturn(appAlice);
        when(tableQueryService.findSeatIndex("g1", "alice")).thenReturn(1);

        var req = new ApplyActionRequest(1, new ApplyActionRequest.ActionDto(ActionType.CALL, 0));
        var response = controller.applyAction("g1", req, null,
                new User("pub-alice", "secret", List.of()));

        assertThat(response.players().get(1).hole()).hasSize(2);
        assertThat(response.players().get(0).hole()).isNull();
    }

    // -----------------------------------------------------------------------
    // getActions
    // -----------------------------------------------------------------------

    @Test
    void getActionsReturnsMappedRecords() {
        when(gameService.getActions("g1")).thenReturn(List.of(
                new ActionRecord(1, 0, ActionType.CALL, 0),
                new ActionRecord(2, 1, ActionType.CHECK, 0)
        ));

        var response = controller.getActions("g1");

        assertThat(response.actions()).hasSize(2);
        assertThat(response.actions().get(0).seq()).isEqualTo(1);
        assertThat(response.actions().get(0).type()).isEqualTo(ActionType.CALL);
        assertThat(response.actions().get(1).seq()).isEqualTo(2);
        assertThat(response.actions().get(1).type()).isEqualTo(ActionType.CHECK);
    }

    @Test
    void getActionsReturnsEmptyWhenNoActions() {
        when(gameService.getActions("g1")).thenReturn(List.of());

        assertThat(controller.getActions("g1").actions()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // resolveShowdown
    // -----------------------------------------------------------------------

    @Test
    void resolveShowdownCallsServiceAndReturnsState() {
        GameState finished = finishedGame();
        when(gameService.resolveShowdown("g1")).thenReturn(null);
        when(gameService.getGame("g1")).thenReturn(finished);

        var response = controller.resolveShowdown("g1");

        verify(gameService).resolveShowdown("g1");
        assertThat(response.status()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void resolveShowdownRevealsAllHoleCards() {
        GameState finished = finishedGame();
        when(gameService.resolveShowdown("g1")).thenReturn(null);
        when(gameService.getGame("g1")).thenReturn(finished);

        var response = controller.resolveShowdown("g1");

        // FINISHED かつ非foldWin → showAll=true → 全員のホールカードが見える
        response.players().forEach(p -> assertThat(p.hole()).isNotNull().hasSize(2));
    }

    // -----------------------------------------------------------------------
    // ヘルパー
    // -----------------------------------------------------------------------

    private static GameState newGame() {
        GameState g = GameState.newGame(
                List.of(new Player("alice", 1000), new Player("bob", 1000)),
                0, 10, new Random(1), OddChipRule.LOW_INDEX);
        g.setId("g1");
        return g;
    }

    private static GameState startedGame() {
        GameState g = newGame();
        g.startHand(10);
        return g;
    }

    /** ショーダウン解決済みの FINISHED 状態ゲームを返す。 */
    private static GameState finishedGame() {
        GameState g = startedGame();
        // preflop: P1(SB/UTG)コール → P0(BB)チェック
        g.applyAction(1, com.mapoker.domain.rules.Action.of(ActionType.CALL, 0));
        g.applyAction(0, com.mapoker.domain.rules.Action.of(ActionType.CHECK, 0));
        // flop: P0 → P1
        g.applyAction(0, com.mapoker.domain.rules.Action.of(ActionType.CHECK, 0));
        g.applyAction(1, com.mapoker.domain.rules.Action.of(ActionType.CHECK, 0));
        // turn: P0 → P1
        g.applyAction(0, com.mapoker.domain.rules.Action.of(ActionType.CHECK, 0));
        g.applyAction(1, com.mapoker.domain.rules.Action.of(ActionType.CHECK, 0));
        // river: P0 → P1
        g.applyAction(0, com.mapoker.domain.rules.Action.of(ActionType.CHECK, 0));
        g.applyAction(1, com.mapoker.domain.rules.Action.of(ActionType.CHECK, 0));
        // showdown 解決
        var result = g.resolveShowdown();
        g.applyPayouts(result.payouts());
        return g;
    }
}
