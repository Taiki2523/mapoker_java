package com.mapoker.infrastructure.persistence;

import com.mapoker.application.ports.OfflinePlayerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@Profile("postgresql")
public class PostgresOfflinePlayerRepository implements OfflinePlayerRepository {

    private final JdbcTemplate jdbc;

    public PostgresOfflinePlayerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void addBuyIn(String gameId, int seatIndex, int amount) {
        jdbc.update("""
                UPDATE players
                SET offline_buy_in = COALESCE(offline_buy_in, 0) + ?
                WHERE game_id = ? AND player_index = ?
                """, amount, gameId, seatIndex);
    }

    @Override
    public void setCashout(String gameId, int seatIndex, int cashoutAmount, Instant cashoutAt) {
        jdbc.update("""
                UPDATE players
                SET offline_cash_out = ?, cashout_at = ?
                WHERE game_id = ? AND player_index = ?
                """, cashoutAmount, Timestamp.from(cashoutAt), gameId, seatIndex);
    }

    @Override
    public List<OfflinePlayerData> findByGameId(String gameId) {
        return jdbc.query("""
                SELECT player_index, offline_buy_in, offline_cash_out, cashout_at
                FROM players
                WHERE game_id = ?
                ORDER BY player_index
                """,
                (rs, row) -> new OfflinePlayerData(
                        rs.getInt("player_index"),
                        rs.getInt("offline_buy_in"),
                        rs.getObject("offline_cash_out", Integer.class),
                        rs.getTimestamp("cashout_at") != null
                                ? rs.getTimestamp("cashout_at").toInstant()
                                : null),
                gameId);
    }
}
