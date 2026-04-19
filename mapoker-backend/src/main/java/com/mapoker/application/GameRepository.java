package com.mapoker.application;

import com.mapoker.domain.game.GameState;
import java.util.List;
import java.util.Optional;

public interface GameRepository {
    void create(String id, GameState state);
    void update(String id, GameState state, ActionRecord action);
    void update(String id, GameState state);
    Optional<GameState> findById(String id);
    List<GameState> findAll();
    List<ActionRecord> findActionsByGameId(String gameId);
}
