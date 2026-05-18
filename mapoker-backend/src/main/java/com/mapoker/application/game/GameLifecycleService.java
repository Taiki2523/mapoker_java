package com.mapoker.application.game;

import com.mapoker.application.ports.GameRepository;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.game.Player;
import com.mapoker.infrastructure.messaging.GameEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * ゲームの作成・ハンド開始・シート操作を担うサービス。
 * TableMembershipService / TableLifecycleService から呼び出される。
 */
@Service
public class GameLifecycleService {

    private final GameRepository gameRepository;
    private final GameReadService readService;
    private final ObjectProvider<GameEventPublisher> eventPublisherProvider;

    public GameLifecycleService(GameRepository gameRepository,
                                GameReadService readService,
                                ObjectProvider<GameEventPublisher> eventPublisherProvider) {
        this.gameRepository = gameRepository;
        this.readService = readService;
        this.eventPublisherProvider = eventPublisherProvider;
    }

    public GameState createGame(List<PlayerInput> playerInputs, int buttonIndex, int bigBlind,
                                Long seed, OddChipRule oddChipRule) {
        List<Player> players = playerInputs.stream()
                .map(pi -> new Player(pi.id(), pi.stack()))
                .toList();
        Random rng = seed != null ? new Random(seed) : new Random();
        GameState state = GameState.newGame(players, buttonIndex, bigBlind, rng, oddChipRule);
        String id = UUID.randomUUID().toString();
        state.setId(id);
        gameRepository.create(id, state);
        return state;
    }

    public GameState createRingGame(List<PlayerInput> playerInputs, int bigBlind, OddChipRule oddChipRule) {
        return createRingGame(playerInputs, bigBlind, oddChipRule, 0);
    }

    public GameState createRingGame(List<PlayerInput> playerInputs, int bigBlind, OddChipRule oddChipRule, int ante) {
        return createRingGame(playerInputs, bigBlind, oddChipRule, ante, false);
    }

    public GameState createRingGame(List<PlayerInput> playerInputs, int bigBlind, OddChipRule oddChipRule,
                                    int ante, boolean straddleEnabled) {
        List<Player> players = playerInputs.stream()
                .map(pi -> new Player(pi.id(), pi.stack()))
                .toList();
        GameState state = GameState.newGame(players, 0, bigBlind, new Random(), oddChipRule, ante, straddleEnabled);
        String id = UUID.randomUUID().toString();
        state.setId(id);
        state.setStatus(GameStatus.FINISHED);
        gameRepository.create(id, state);
        return state;
    }

    public GameState startHand(String id, int bigBlind) {
        return startHand(id, bigBlind, false);
    }

    public GameState startHand(String id, int bigBlind, boolean doStraddle) {
        GameState state = readService.getGame(id);
        state.startHand(bigBlind, doStraddle);
        gameRepository.update(id, state);
        publishGame(id, state);
        publishHoleCards(id, state);
        return state;
    }

    public void setStraddleIntent(String id, boolean straddle) {
        GameState state = readService.getGame(id);
        state.setNextHandStraddle(straddle);
        gameRepository.update(id, state);
    }

    public void setButtonIndex(String tableId, int buttonIndex) {
        GameState state = readService.getGame(tableId);
        state.setButtonIndex(buttonIndex);
        gameRepository.update(tableId, state);
    }

    public void setSeatStack(String tableId, int seatIndex, int amount) {
        GameState state = readService.getGame(tableId);
        state.getPlayers().get(seatIndex).setStack(amount);
        gameRepository.update(tableId, state);
    }

    public void setSittingOut(String tableId, int seatIndex, boolean value) {
        GameState state = readService.getGame(tableId);
        state.getPlayers().get(seatIndex).setSittingOut(value);
        gameRepository.update(tableId, state);
    }

    public int getSeatStack(String tableId, int seatIndex) {
        return readService.getGame(tableId).getPlayers().get(seatIndex).getStack();
    }

    private void publishGame(String tableId, GameState state) {
        GameEventPublisher pub = eventPublisherProvider.getIfAvailable();
        if (pub != null) {
            pub.publishGameState(tableId, state, null);
        }
    }

    private void publishHoleCards(String tableId, GameState state) {
        GameEventPublisher pub = eventPublisherProvider.getIfAvailable();
        if (pub != null) {
            pub.publishHoleCards(tableId, state);
        }
    }

    /** ゲーム作成時のプレイヤー入力パラメータ。 */
    public record PlayerInput(String id, int stack) {}
}
