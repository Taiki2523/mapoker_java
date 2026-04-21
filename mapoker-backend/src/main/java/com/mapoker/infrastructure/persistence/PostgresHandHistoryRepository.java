package com.mapoker.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapoker.application.HandHistoryEntry;
import com.mapoker.application.HandHistoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

@Repository
@Profile("postgresql")
public class PostgresHandHistoryRepository implements HandHistoryRepository {

    private static final TypeReference<List<HandHistoryEntry.PlayerSnapshot>> PLAYER_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Integer>> INTEGER_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresHandHistoryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void save(HandHistoryEntry entry) {
        jdbc.update("""
                INSERT INTO hand_history (table_id, hand_id, players, winners, pot, street, finished_at)
                VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, CAST(? AS game_street), ?)
                """,
                entry.tableId(),
                entry.handId(),
                toJson(entry.players()),
                toJson(entry.winners()),
                entry.pot(),
                entry.street(),
                Timestamp.from(entry.finishedAt()));
    }

    @Override
    public List<HandHistoryEntry> findRecentByTableIds(List<String> tableIds, int limit) {
        if (tableIds == null || tableIds.isEmpty()) {
            return List.of();
        }
        List<String> uniqueTableIds = List.copyOf(new LinkedHashSet<>(tableIds));
        String placeholders = String.join(", ", Collections.nCopies(uniqueTableIds.size(), "?"));
        String sql = """
                SELECT table_id, hand_id, players, winners, pot, street, finished_at
                FROM hand_history
                WHERE table_id IN (%s)
                ORDER BY finished_at DESC
                LIMIT ?
                """.formatted(placeholders);
        Object[] params = new Object[uniqueTableIds.size() + 1];
        for (int i = 0; i < uniqueTableIds.size(); i++) {
            params[i] = uniqueTableIds.get(i);
        }
        params[uniqueTableIds.size()] = Math.max(1, limit);
        return jdbc.query(sql, (rs, rowNum) -> mapEntry(rs), params);
    }

    private HandHistoryEntry mapEntry(ResultSet rs) throws SQLException {
        return new HandHistoryEntry(
                rs.getString("table_id"),
                rs.getString("hand_id"),
                fromJson(rs.getString("players"), PLAYER_LIST),
                fromJson(rs.getString("winners"), INTEGER_LIST),
                rs.getInt("pot"),
                rs.getString("street"),
                rs.getTimestamp("finished_at").toInstant()
        );
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return mapper.readValue(json, typeReference);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization failed: " + e.getMessage(), e);
        }
    }
}
