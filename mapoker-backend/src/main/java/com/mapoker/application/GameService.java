package com.mapoker.application;

import com.mapoker.domain.card.Card;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.game.Player;
import com.mapoker.domain.game.ShowdownResult;
import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;

@Service
public class GameService {

    private static final String MASKED_HOLE_CARD = "??";

    private final GameRepository gameRepository;
    private final HandHistoryService handHistoryService;
    private final ObjectProvider<TableService> tableServiceProvider;

    public GameService(GameRepository gameRepository,
                       HandHistoryService handHistoryService,
                       ObjectProvider<TableService> tableServiceProvider) {
        this.gameRepository = gameRepository;
        this.handHistoryService = handHistoryService;
        this.tableServiceProvider = tableServiceProvider;
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

    public GameState createRingGame(List<PlayerInput> playerInputs, int bigBlind, OddChipRule oddChipRule) {
        List<Player> players = playerInputs.stream()
                .map(pi -> new Player(pi.id(), pi.stack()))
                .toList();
        GameState state = GameState.newGame(players, 0, bigBlind, new Random(), oddChipRule);
        String id = UUID.randomUUID().toString();
        state.setId(id);
        state.setStatus(GameStatus.FINISHED);
        gameRepository.create(id, state);
        return state;
    }

    public void setButtonIndex(String tableId, int buttonIndex) {
        GameState state = getGame(tableId);
        state.setButtonIndex(buttonIndex);
        gameRepository.update(tableId, state);
    }

    public void setSeatStack(String tableId, int seatIndex, int amount) {
        GameState state = getGame(tableId);
        Player player = state.getPlayers().get(seatIndex);
        player.setStack(amount);
        gameRepository.update(tableId, state);
    }

    public void setSittingOut(String tableId, int seatIndex, boolean value) {
        GameState state = getGame(tableId);
        state.getPlayers().get(seatIndex).setSittingOut(value);
        gameRepository.update(tableId, state);
    }

    public int getSeatStack(String tableId, int seatIndex) {
        GameState state = getGame(tableId);
        return state.getPlayers().get(seatIndex).getStack();
    }

    public GameState applyAction(String id, int playerIndex, ActionType type, int amount) {
        GameState state = getGame(id);
        Action action = Action.of(type, amount);
        state.applyAction(playerIndex, action);
        ActionRecord record = new ActionRecord(
                gameRepository.findActionsByGameId(id).size() + 1,
                playerIndex, type, amount);
        gameRepository.update(id, state, record);
        recordHandHistoryIfFinished(id, state);
        if (state.isFoldWin()) {
            TableService tableService = tableServiceProvider.getIfAvailable();
            if (tableService != null) {
                tableService.processPendingLeaves(id);
            }
        }
        return state;
    }

    public List<ActionRecord> getActions(String id) {
        getGame(id);
        return gameRepository.findActionsByGameId(id);
    }

    public ShowdownResult resolveShowdown(String id) {
        GameState state = getGame(id);
        ShowdownResult result = state.resolveShowdown();
        state.applyPayouts(result.payouts());
        gameRepository.update(id, state);
        recordHandHistoryIfFinished(id, state);
        TableService tableService = tableServiceProvider.getIfAvailable();
        if (tableService != null) {
            tableService.processPendingLeaves(id);
        }
        return result;
    }

    private void recordHandHistoryIfFinished(String tableId, GameState state) {
        if (state.getStatus() != GameStatus.FINISHED || state.getLastShowdown() == null) {
            return;
        }
        handHistoryService.record(buildHandHistoryEntry(tableId, state));
    }

    private HandHistoryEntry buildHandHistoryEntry(String tableId, GameState state) {
        List<Integer> payouts = state.getLastShowdown().payouts();
        List<HandHistoryEntry.PlayerSnapshot> players = new ArrayList<>();
        List<TableMemberRecord> members = lookupMembers(tableId);
        for (int seatIndex = 0; seatIndex < state.getPlayers().size(); seatIndex++) {
            Player player = state.getPlayers().get(seatIndex);
            int payout = seatIndex < payouts.size() ? payouts.get(seatIndex) : 0;
            int stackAfter = player.getStack();
            int stackBefore = stackAfter + player.getTotalContrib() - payout;
            players.add(new HandHistoryEntry.PlayerSnapshot(
                    resolvePlayerName(members, seatIndex, player.getId()),
                    seatIndex,
                    stackBefore,
                    stackAfter,
                    player.isFolded(),
                    maskHoleCards(player.getHole())
            ));
        }
        return new HandHistoryEntry(
                tableId,
                UUID.randomUUID().toString(),
                players,
                state.getLastShowdown().winnerIndexes(),
                payouts.stream().mapToInt(Integer::intValue).sum(),
                state.getStreet().getLabel(),
                Instant.now()
        );
    }

    private List<TableMemberRecord> lookupMembers(String tableId) {
        TableService tableService = tableServiceProvider.getIfAvailable();
        if (tableService == null) {
            return List.of();
        }
        try {
            return tableService.getMembers(tableId);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private String resolvePlayerName(List<TableMemberRecord> members, int seatIndex, String fallback) {
        return members.stream()
                .filter(member -> member.seatIndex() == seatIndex)
                .map(TableMemberRecord::name)
                .findFirst()
                .orElseGet(() -> (fallback != null && !fallback.isBlank())
                        ? fallback
                        : "Seat " + (seatIndex + 1));
    }

    private List<String> maskHoleCards(Card[] hole) {
        if (hole == null) {
            return List.of();
        }
        List<String> masked = new ArrayList<>();
        for (Card card : hole) {
            if (card != null) {
                masked.add(MASKED_HOLE_CARD);
            }
        }
        return List.copyOf(masked);
    }

    public record PlayerInput(String id, int stack) {}
}
