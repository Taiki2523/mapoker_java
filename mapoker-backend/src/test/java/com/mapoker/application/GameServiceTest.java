package com.mapoker.application;

import com.mapoker.application.game.ActionRecord;
import com.mapoker.application.game.GameActionService;
import com.mapoker.application.game.GameLifecycleService;
import com.mapoker.application.game.GameReadService;
import com.mapoker.application.history.HandHistoryService;
import com.mapoker.application.history.UserTableHistoryService;
import com.mapoker.application.table.TableLifecycleService;
import com.mapoker.application.table.TableMembershipService;
import com.mapoker.application.table.TableQueryService;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.rules.ActionType;
import com.mapoker.infrastructure.messaging.GameEventPublisher;
import com.mapoker.infrastructure.persistence.InMemoryGameRepository;
import com.mapoker.infrastructure.persistence.InMemoryHandHistoryRepository;
import com.mapoker.infrastructure.persistence.InMemoryUserTableHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * GameService の単体テスト。
 *
 * <p>Spring を起動せず、InMemory 実装を直接注入する。
 * TableService・GameEventPublisher は null を返す ObjectProvider で stub する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServiceTest {

    @Mock ObjectProvider<TableMembershipService> tableMembershipProvider;
    @Mock ObjectProvider<TableQueryService> tableQueryProvider;
    @Mock ObjectProvider<GameEventPublisher> publisherProvider;

    private GameReadService gameRead;
    private GameLifecycleService gameLifecycle;
    private GameActionService gameAction;
    private InMemoryGameRepository gameRepo;

    private static final List<GameLifecycleService.PlayerInput> TWO_PLAYERS = List.of(
            new GameLifecycleService.PlayerInput("alice", 1000),
            new GameLifecycleService.PlayerInput("bob", 1000)
    );

    @BeforeEach
    void setUp() {
        when(tableMembershipProvider.getIfAvailable()).thenReturn(null);
        when(tableQueryProvider.getIfAvailable()).thenReturn(null);
        when(publisherProvider.getIfAvailable()).thenReturn(null);

        gameRepo = new InMemoryGameRepository();
        var historyRepo = new InMemoryHandHistoryRepository();
        var userTableHistoryRepo = new InMemoryUserTableHistoryRepository();
        var userTableHistoryService = new UserTableHistoryService(userTableHistoryRepo);
        var handHistoryService = new HandHistoryService(historyRepo, userTableHistoryService);

        gameRead = new GameReadService(gameRepo);
        gameLifecycle = new GameLifecycleService(gameRepo, gameRead, publisherProvider);
        gameAction = new GameActionService(gameRepo, gameRead, handHistoryService, tableMembershipProvider, tableQueryProvider, publisherProvider);
    }

    // -----------------------------------------------------------------------
    // createGame
    // -----------------------------------------------------------------------

    @Test
    void createGameReturnsStateWithCorrectPlayers() {
        GameState state = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        assertThat(state.getPlayers()).hasSize(2);
        assertThat(state.getPlayers().get(0).getId()).isEqualTo("alice");
        assertThat(state.getPlayers().get(1).getId()).isEqualTo("bob");
    }

    @Test
    void createGamePersistsToRepository() {
        GameState state = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        assertThat(gameRepo.findById(state.getId())).isPresent();
    }

    @Test
    void createGameWithSeedIsReproducible() {
        GameState s1 = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, 42L, OddChipRule.LOW_INDEX);
        GameState s2 = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, 42L, OddChipRule.LOW_INDEX);
        assertThat(s1.getId()).isNotEqualTo(s2.getId());
    }

    // -----------------------------------------------------------------------
    // getGame
    // -----------------------------------------------------------------------

    @Test
    void getGameReturnsPersistedState() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        GameState fetched = gameRead.getGame(created.getId());
        assertThat(fetched.getId()).isEqualTo(created.getId());
    }

    @Test
    void getGameThrowsForUnknownId() {
        assertThatThrownBy(() -> gameRead.getGame("nonexistent"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("nonexistent");
    }

    // -----------------------------------------------------------------------
    // listGames
    // -----------------------------------------------------------------------

    @Test
    void listGamesReturnsAllCreatedGames() {
        gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        assertThat(gameRead.listGames()).hasSize(2);
    }

    @Test
    void listGamesReturnsEmptyWhenNoGames() {
        assertThat(gameRead.listGames()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // createRingGame
    // -----------------------------------------------------------------------

    @Test
    void createRingGameStatusIsFinished() {
        GameState state = gameLifecycle.createRingGame(TWO_PLAYERS, 10, OddChipRule.LOW_INDEX);
        assertThat(state.getStatus()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void createRingGameIsPersisted() {
        GameState state = gameLifecycle.createRingGame(TWO_PLAYERS, 10, OddChipRule.LOW_INDEX);
        assertThat(gameRepo.findById(state.getId())).isPresent();
    }

    // -----------------------------------------------------------------------
    // startHand
    // -----------------------------------------------------------------------

    @Test
    void startHandChangesStatusToInProgress() {
        GameState created = gameLifecycle.createRingGame(TWO_PLAYERS, 10, OddChipRule.LOW_INDEX);
        gameLifecycle.setSeatStack(created.getId(), 0, 1000);
        gameLifecycle.setSeatStack(created.getId(), 1, 1000);
        GameState started = gameLifecycle.startHand(created.getId(), 10);
        assertThat(started.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    void startHandDealsHoleCardsToPlayers() {
        GameState created = gameLifecycle.createRingGame(TWO_PLAYERS, 10, OddChipRule.LOW_INDEX);
        gameLifecycle.setSeatStack(created.getId(), 0, 1000);
        gameLifecycle.setSeatStack(created.getId(), 1, 1000);
        GameState started = gameLifecycle.startHand(created.getId(), 10);
        assertThat(started.getPlayers().get(0).getHole()).isNotNull().hasSize(2);
        assertThat(started.getPlayers().get(1).getHole()).isNotNull().hasSize(2);
    }

    // -----------------------------------------------------------------------
    // setButtonIndex / setSeatStack / getSeatStack / setSittingOut
    // -----------------------------------------------------------------------

    @Test
    void setButtonIndexUpdatesButtonPosition() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.setButtonIndex(created.getId(), 1);
        GameState fetched = gameRead.getGame(created.getId());
        assertThat(fetched.getButtonIndex()).isEqualTo(1);
    }

    @Test
    void setSeatStackUpdatesStack() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.setSeatStack(created.getId(), 0, 500);
        assertThat(gameLifecycle.getSeatStack(created.getId(), 0)).isEqualTo(500);
    }

    @Test
    void setSittingOutMarksPlayerCorrectly() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.setSittingOut(created.getId(), 0, true);
        GameState fetched = gameRead.getGame(created.getId());
        assertThat(fetched.getPlayers().get(0).isSittingOut()).isTrue();
    }

    // -----------------------------------------------------------------------
    // applyAction
    // -----------------------------------------------------------------------

    @Test
    void applyActionRecordsActionHistory() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.startHand(created.getId(), 10);

        // startHand でボタンが0→1に移動。SB=P1, BB=P0。プリフロップ最初のアクターはP1
        gameAction.applyAction(created.getId(), 1, ActionType.CALL, 0);

        List<ActionRecord> actions = gameRead.getActions(created.getId());
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).actionType()).isEqualTo(ActionType.CALL);
        assertThat(actions.get(0).playerIndex()).isEqualTo(1);
    }

    @Test
    void applyActionFoldWinUpdatesStatus() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.startHand(created.getId(), 10);

        // P1（SB/プリフロップUTG）がフォールド → fold-win
        GameState result = gameAction.applyAction(created.getId(), 1, ActionType.FOLD, 0);
        assertThat(result.getStatus()).isEqualTo(GameStatus.FINISHED);
        assertThat(result.isFoldWin()).isTrue();
    }

    // -----------------------------------------------------------------------
    // getActions
    // -----------------------------------------------------------------------

    @Test
    void getActionsReturnsEmptyBeforeAnyAction() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.startHand(created.getId(), 10);
        assertThat(gameRead.getActions(created.getId())).isEmpty();
    }

    @Test
    void getActionsThrowsForUnknownGame() {
        assertThatThrownBy(() -> gameRead.getActions("unknown-id"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getActionsSeqIsSequential() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.startHand(created.getId(), 10);
        // プリフロップ: P1(SB/UTG)コール → P0(BB)チェック
        gameAction.applyAction(created.getId(), 1, ActionType.CALL, 0);
        gameAction.applyAction(created.getId(), 0, ActionType.CHECK, 0);

        List<ActionRecord> actions = gameRead.getActions(created.getId());
        assertThat(actions).hasSize(2);
        assertThat(actions.get(0).seq()).isEqualTo(1);
        assertThat(actions.get(1).seq()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // resolveShowdown
    // -----------------------------------------------------------------------

    @Test
    void resolveShowdownSetsStatusToFinished() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.startHand(created.getId(), 10);
        advanceToShowdown(created.getId());

        gameAction.resolveShowdown(created.getId());
        GameState after = gameRead.getGame(created.getId());
        assertThat(after.getStatus()).isEqualTo(GameStatus.FINISHED);
    }

    @Test
    void resolveShowdownReturnsNonNullResult() {
        GameState created = gameLifecycle.createGame(TWO_PLAYERS, 0, 10, null, OddChipRule.LOW_INDEX);
        gameLifecycle.startHand(created.getId(), 10);
        advanceToShowdown(created.getId());

        var result = gameAction.resolveShowdown(created.getId());
        assertThat(result).isNotNull();
        assertThat(result.payouts()).hasSize(2);
    }

    /**
     * 2人ゲームをショーダウンまで進める。
     * startHand でボタンが0→1に移動。SB=P1, BB=P0。
     * preflop: P1(UTG)コール → P0(BB)チェック
     * flop/turn/river: firstToAct = nextActive(buttonIndex=1) = P0 → P1
     */
    private void advanceToShowdown(String gameId) {
        gameAction.applyAction(gameId, 1, ActionType.CALL, 0);
        gameAction.applyAction(gameId, 0, ActionType.CHECK, 0);
        gameAction.applyAction(gameId, 0, ActionType.CHECK, 0);
        gameAction.applyAction(gameId, 1, ActionType.CHECK, 0);
        gameAction.applyAction(gameId, 0, ActionType.CHECK, 0);
        gameAction.applyAction(gameId, 1, ActionType.CHECK, 0);
        gameAction.applyAction(gameId, 0, ActionType.CHECK, 0);
        gameAction.applyAction(gameId, 1, ActionType.CHECK, 0);
    }
}
