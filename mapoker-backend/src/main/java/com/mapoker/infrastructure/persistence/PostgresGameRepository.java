package com.mapoker.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapoker.application.ActionRecord;
import com.mapoker.application.GameRepository;
import com.mapoker.domain.card.Card;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.game.Player;
import com.mapoker.domain.game.ShowdownResult;
import com.mapoker.domain.rules.ActionType;
import com.mapoker.domain.rules.Street;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring の {@code @Repository} として PostgreSQL へゲーム状態を永続化する実装です。
 */
@Repository
@Profile("postgresql")
public class PostgresGameRepository implements GameRepository {

    private static final TypeReference<List<Card>> CARD_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresGameRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * 新しいゲームを永続化します。
     *
     * @param id ゲーム ID
     * @param state 保存するゲーム状態
     */
    @Override
    @Transactional
    public void create(String id, GameState state) {
        state.setId(id);
        insertGame(id, state);
        insertPlayers(id, state);
    }

    /**
     * ゲーム状態とアクション履歴を更新します。
     *
     * @param id ゲーム ID
     * @param state 更新後のゲーム状態
     * @param action 追加するアクション履歴
     */
    @Override
    @Transactional
    public void update(String id, GameState state, ActionRecord action) {
        state.setId(id);
        updateGame(id, state);
        updatePlayers(id, state);
        insertAction(id, action);
    }

    /**
     * ゲーム状態のみを更新します。
     *
     * @param id ゲーム ID
     * @param state 更新後のゲーム状態
     */
    @Override
    @Transactional
    public void update(String id, GameState state) {
        state.setId(id);
        updateGame(id, state);
        updatePlayers(id, state);
    }

    /**
     * ID でゲーム状態を取得します。
     *
     * @param id ゲーム ID
     * @return 見つかったゲーム状態
     */
    @Override
    public Optional<GameState> findById(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM games WHERE id = ?", id);
        if (rows.isEmpty()) return Optional.empty();
        List<Map<String, Object>> playerRows = jdbc.queryForList(
                "SELECT * FROM players WHERE game_id = ? ORDER BY player_index", id);
        return Optional.of(toGameState(rows.get(0), playerRows));
    }

    /**
     * すべてのゲーム状態を取得します。
     *
     * @return ゲーム状態一覧
     */
    @Override
    public List<GameState> findAll() {
        List<Map<String, Object>> gameRows = jdbc.queryForList(
                "SELECT * FROM games ORDER BY created_at");
        if (gameRows.isEmpty()) return List.of();

        List<Map<String, Object>> playerRows = jdbc.queryForList(
                "SELECT * FROM players ORDER BY game_id, player_index");

        Map<String, List<Map<String, Object>>> playersByGame = new LinkedHashMap<>();
        for (Map<String, Object> pr : playerRows) {
            String gid = (String) pr.get("game_id");
            playersByGame.computeIfAbsent(gid, k -> new ArrayList<>()).add(pr);
        }

        List<GameState> result = new ArrayList<>();
        for (Map<String, Object> gr : gameRows) {
            String gid = (String) gr.get("id");
            result.add(toGameState(gr, playersByGame.getOrDefault(gid, List.of())));
        }
        return result;
    }

    /**
     * 指定ゲームのアクション履歴を取得します。
     *
     * @param gameId ゲーム ID
     * @return アクション履歴一覧
     */
    @Override
    public List<ActionRecord> findActionsByGameId(String gameId) {
        return jdbc.query(
                "SELECT * FROM actions WHERE game_id = ? ORDER BY seq",
                (rs, rowNum) -> new ActionRecord(
                        rs.getInt("seq"),
                        rs.getInt("player_index"),
                        ActionType.fromLabel(rs.getString("action_type")),
                        rs.getInt("amount"),
                        rs.getString("label")),
                gameId);
    }

    // ---- write helpers ----

    private void insertGame(String id, GameState s) {
        jdbc.update("""
                INSERT INTO games (
                  id, status, street, button_index, small_blind_idx, big_blind_idx,
                  current_player, current_bet, last_raise_size, big_blind, pot_total,
                  odd_chip_rule, deck, deck_pos, community, acted, raise_open, fold_win, last_showdown,
                  ante
                ) VALUES (
                  ?, CAST(? AS game_status), CAST(? AS game_street), ?, ?, ?, ?, ?, ?, ?, ?,
                  CAST(? AS odd_chip_rule),
                  CAST(? AS jsonb), ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, CAST(? AS jsonb),
                  ?
                )
                """,
                id,
                s.getStatus().getLabel(),
                s.getStreet().getLabel(),
                s.getButtonIndex(),
                s.getSmallBlindIdx(),
                s.getBigBlindIdx(),
                s.getCurrentPlayer(),
                s.getCurrentBet(),
                s.getLastRaiseSize(),
                s.getBigBlindSize(),
                s.getPot(),
                s.getOddChipRule().getLabel(),
                toJson(s.getDeck()),
                s.getDeckPos(),
                toJson(s.getCommunity()),
                toJson(s.getActed()),
                s.isRaiseOpen(),
                s.isFoldWin(),
                s.getLastShowdown() != null ? toJson(s.getLastShowdown()) : null,
                s.getAnte());
    }

    private void updateGame(String id, GameState s) {
        jdbc.update("""
                UPDATE games SET
                  status          = CAST(? AS game_status),
                  street          = CAST(? AS game_street),
                  button_index    = ?,
                  small_blind_idx = ?,
                  big_blind_idx   = ?,
                  current_player  = ?,
                  current_bet     = ?,
                  last_raise_size = ?,
                  big_blind       = ?,
                  pot_total       = ?,
                  odd_chip_rule   = CAST(? AS odd_chip_rule),
                  deck            = CAST(? AS jsonb),
                  deck_pos        = ?,
                  community       = CAST(? AS jsonb),
                  acted           = CAST(? AS jsonb),
                  raise_open      = ?,
                  fold_win        = ?,
                  last_showdown   = CAST(? AS jsonb),
                  ante            = ?,
                  updated_at      = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                s.getStatus().getLabel(),
                s.getStreet().getLabel(),
                s.getButtonIndex(),
                s.getSmallBlindIdx(),
                s.getBigBlindIdx(),
                s.getCurrentPlayer(),
                s.getCurrentBet(),
                s.getLastRaiseSize(),
                s.getBigBlindSize(),
                s.getPot(),
                s.getOddChipRule().getLabel(),
                toJson(s.getDeck()),
                s.getDeckPos(),
                toJson(s.getCommunity()),
                toJson(s.getActed()),
                s.isRaiseOpen(),
                s.isFoldWin(),
                s.getLastShowdown() != null ? toJson(s.getLastShowdown()) : null,
                s.getAnte(),
                id);
    }

    private void insertPlayers(String id, GameState s) {
        List<Player> players = s.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            jdbc.update("""
                    INSERT INTO players (game_id, player_index, player_id, stack, contributed, total_contrib, folded, all_in, hole, sitting_out)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                    """,
                    id, i, p.getId(), p.getStack(), p.getContributed(),
                    p.getTotalContrib(), p.isFolded(), p.isAllIn(),
                    toJson(p.getHole()), p.isSittingOut());
        }
    }

    private void updatePlayers(String id, GameState s) {
        List<Player> players = s.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            jdbc.update("""
                    UPDATE players SET
                      stack         = ?,
                      contributed   = ?,
                      total_contrib = ?,
                      folded        = ?,
                      all_in        = ?,
                      sitting_out   = ?,
                      hole          = CAST(? AS jsonb),
                      updated_at    = CURRENT_TIMESTAMP
                    WHERE game_id = ? AND player_index = ?
                    """,
                    p.getStack(), p.getContributed(), p.getTotalContrib(),
                    p.isFolded(), p.isAllIn(), p.isSittingOut(), toJson(p.getHole()),
                    id, i);
        }
    }

    @Override
    public void appendAction(String id, ActionRecord action) {
        insertAction(id, action);
    }

    private void insertAction(String id, ActionRecord action) {
        jdbc.update("""
                INSERT INTO actions (game_id, seq, player_index, action_type, amount, label)
                VALUES (?, ?, ?, CAST(? AS action_type), ?, ?)
                """,
                id, action.seq(), action.playerIndex(),
                action.actionType().getLabel(), action.amount(), action.label());
    }

    // ---- deserialize ----

    private GameState toGameState(Map<String, Object> row, List<Map<String, Object>> playerRows) {
        GameState s = GameState.empty();
        s.setId((String) row.get("id"));
        s.setStatus(statusFromLabel((String) row.get("status")));
        s.setStreet(streetFromLabel((String) row.get("street")));
        s.setButtonIndex((Integer) row.get("button_index"));
        s.setSmallBlindIdx((Integer) row.get("small_blind_idx"));
        s.setBigBlindIdx((Integer) row.get("big_blind_idx"));
        s.setCurrentPlayer((Integer) row.get("current_player"));
        s.setCurrentBet((Integer) row.get("current_bet"));
        s.setLastRaiseSize((Integer) row.get("last_raise_size"));
        s.setBigBlindSize((Integer) row.get("big_blind"));
        s.setPot((Integer) row.get("pot_total"));
        s.setOddChipRule(OddChipRule.fromLabel((String) row.get("odd_chip_rule")));
        s.setDeck(fromJson(str(row.get("deck")), CARD_LIST));
        s.setDeckPos((Integer) row.get("deck_pos"));
        s.setCommunity(fromJson(str(row.get("community")), CARD_LIST));
        s.setActed(fromJson(str(row.get("acted")), boolean[].class));
        s.setRaiseOpen((Boolean) row.get("raise_open"));
        Object foldWinRaw = row.get("fold_win");
        if (foldWinRaw != null) {
            s.setFoldWin((Boolean) foldWinRaw);
        }
        Object sdRaw = row.get("last_showdown");
        if (sdRaw != null) {
            s.setLastShowdown(fromJson(str(sdRaw), ShowdownResult.class));
        }
        Object anteRaw = row.get("ante");
        if (anteRaw != null) {
            s.setAnte((Integer) anteRaw);
        }

        List<Player> players = new ArrayList<>();
        for (Map<String, Object> pr : playerRows) {
            Player p = new Player((String) pr.get("player_id"), (Integer) pr.get("stack"));
            p.setContributed((Integer) pr.get("contributed"));
            p.setTotalContrib((Integer) pr.get("total_contrib"));
            p.setFolded((Boolean) pr.get("folded"));
            p.setAllIn((Boolean) pr.get("all_in"));
            Object sittingOutRaw = pr.get("sitting_out");
            if (sittingOutRaw != null) {
                p.setSittingOut((Boolean) sittingOutRaw);
            }
            p.setHole(fromJson(str(pr.get("hole")), Card[].class));
            players.add(p);
        }
        s.setPlayers(players);
        return s;
    }

    // ---- JSON helpers ----

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization failed: " + e.getMessage(), e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization failed: " + e.getMessage(), e);
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static GameStatus statusFromLabel(String label) {
        for (GameStatus s : GameStatus.values()) {
            if (s.getLabel().equals(label)) return s;
        }
        throw new IllegalArgumentException("Unknown status: " + label);
    }

    private static Street streetFromLabel(String label) {
        for (Street s : Street.values()) {
            if (s.getLabel().equals(label)) return s;
        }
        throw new IllegalArgumentException("Unknown street: " + label);
    }
}
