package com.mapoker.application;

import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.game.Player;
import com.mapoker.domain.game.ShowdownResult;
import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;

@Service
public class GameService {

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
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

    public GameState getGame(String id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("game not found: " + id));
    }

    public List<GameState> listGames() {
        return gameRepository.findAll();
    }

    public GameState startHand(String id, int bigBlind) {
        GameState state = getGame(id);
        state.startHand(bigBlind);
        gameRepository.update(id, state);
        return state;
    }

    public GameState applyAction(String id, int playerIndex, ActionType type, int amount) {
        GameState state = getGame(id);
        Action action = Action.of(type, amount);
        state.applyAction(playerIndex, action);
        ActionRecord record = new ActionRecord(
                gameRepository.findActionsByGameId(id).size() + 1,
                playerIndex, type, amount);
        gameRepository.update(id, state, record);
        return state;
    }

    public List<ActionRecord> getActions(String id) {
        getGame(id); // existence check
        return gameRepository.findActionsByGameId(id);
    }

    public ShowdownResult resolveShowdown(String id) {
        GameState state = getGame(id);
        ShowdownResult result = state.resolveShowdown();
        state.applyPayouts(result.payouts());
        gameRepository.update(id, state);
        return result;
    }

    public record PlayerInput(String id, int stack) {}
}
