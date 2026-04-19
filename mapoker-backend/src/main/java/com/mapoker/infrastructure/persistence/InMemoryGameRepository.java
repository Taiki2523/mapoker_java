package com.mapoker.infrastructure.persistence;

import com.mapoker.application.ActionRecord;
import com.mapoker.application.GameRepository;
import com.mapoker.domain.game.GameState;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Profile("local")
public class InMemoryGameRepository implements GameRepository {

    private final Map<String, GameState> games = new LinkedHashMap<>();
    private final Map<String, List<ActionRecord>> actions = new LinkedHashMap<>();

    @Override
    public void create(String id, GameState state) {
        state.setId(id);
        games.put(id, state);
        actions.put(id, new ArrayList<>());
    }

    @Override
    public void update(String id, GameState state, ActionRecord action) {
        state.setId(id);
        games.put(id, state);
        actions.computeIfAbsent(id, k -> new ArrayList<>()).add(action);
    }

    @Override
    public void update(String id, GameState state) {
        state.setId(id);
        games.put(id, state);
    }

    @Override
    public Optional<GameState> findById(String id) {
        return Optional.ofNullable(games.get(id));
    }

    @Override
    public List<GameState> findAll() {
        return new ArrayList<>(games.values());
    }

    @Override
    public List<ActionRecord> findActionsByGameId(String gameId) {
        return actions.getOrDefault(gameId, List.of());
    }
}
