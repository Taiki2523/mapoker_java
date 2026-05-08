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

/**
 * Spring の {@code @Repository} としてローカル実行向けにゲーム状態をメモリ保持する実装です。
 */
@Repository
@Profile("local")
public class InMemoryGameRepository implements GameRepository {

    private final Map<String, GameState> games = new LinkedHashMap<>();
    private final Map<String, List<ActionRecord>> actions = new LinkedHashMap<>();

    /**
     * 新しいゲームを保存します。
     *
     * @param id ゲーム ID
     * @param state 保存するゲーム状態
     */
    @Override
    public void create(String id, GameState state) {
        state.setId(id);
        games.put(id, state);
        actions.put(id, new ArrayList<>());
    }

    /**
     * ゲーム状態とアクション履歴を更新します。
     *
     * @param id ゲーム ID
     * @param state 更新後のゲーム状態
     * @param action 追加するアクション履歴
     */
    @Override
    public void update(String id, GameState state, ActionRecord action) {
        state.setId(id);
        games.put(id, state);
        actions.computeIfAbsent(id, k -> new ArrayList<>()).add(action);
    }

    /**
     * ゲーム状態のみを更新します。
     *
     * @param id ゲーム ID
     * @param state 更新後のゲーム状態
     */
    @Override
    public void update(String id, GameState state) {
        state.setId(id);
        games.put(id, state);
    }

    /**
     * ID でゲーム状態を取得します。
     *
     * @param id ゲーム ID
     * @return 見つかったゲーム状態
     */
    @Override
    public Optional<GameState> findById(String id) {
        return Optional.ofNullable(games.get(id));
    }

    /**
     * 保存済みゲームを全件取得します。
     *
     * @return ゲーム状態一覧
     */
    @Override
    public List<GameState> findAll() {
        return new ArrayList<>(games.values());
    }

    /**
     * 指定ゲームのアクション履歴を取得します。
     *
     * @param gameId ゲーム ID
     * @return アクション履歴一覧
     */
    @Override
    public List<ActionRecord> findActionsByGameId(String gameId) {
        return actions.getOrDefault(gameId, List.of());
    }
}
