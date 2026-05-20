package com.mapoker.application.game;

import com.mapoker.application.ports.GameRepository;
import com.mapoker.domain.game.GameState;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/** ゲーム状態の参照を担うサービス。副作用を持たない読み取り専用操作を提供する。 */
@Service
public class GameReadService {

    private final GameRepository gameRepository;

    public GameReadService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public GameState getGame(String id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("game not found: " + id));
    }

    public List<GameState> listGames() {
        return gameRepository.findAll();
    }

    public List<ActionRecord> getActions(String id) {
        getGame(id); // 存在確認
        return gameRepository.findActionsByGameId(id);
    }
}
