package com.mapoker.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapoker.application.TableRecord;
import com.mapoker.application.UserTableHistoryEntry;
import com.mapoker.application.UserTableHistoryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Spring の {@code @Repository} として PostgreSQL へ参加履歴を保存する実装です。
 */
@Repository
@Profile("postgresql")
public class PostgresUserTableHistoryRepository implements UserTableHistoryRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PostgresUserTableHistoryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * テーブル参加を履歴へ記録します。
     *
     * @param username ユーザー名
     * @param table 参加テーブル
     * @param seatIndex 着席位置
     */
    @Override
    public void recordJoin(String username, TableRecord table, int seatIndex) {
        int updated = jdbc.update("""
                UPDATE user_table_sessions
                SET table_name = ?, visibility = ?, status = ?, flags = CAST(? AS jsonb), updated_at = CURRENT_TIMESTAMP
                WHERE username = ? AND table_id = ? AND seat_index = ? AND left_at IS NULL
                """,
                table.name(),
                table.visibility(),
                table.status(),
                toJson(table.flags()),
                username,
                table.id(),
                seatIndex);
        if (updated > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO user_table_sessions (username, table_id, table_name, seat_index, visibility, status, flags)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """,
                username,
                table.id(),
                table.name(),
                seatIndex,
                table.visibility(),
                table.status(),
                toJson(table.flags()));
    }

    /**
     * テーブル退出を履歴へ記録します。
     *
     * @param username ユーザー名
     * @param tableId テーブル ID
     * @param seatIndex 着席位置
     */
    @Override
    public void recordLeave(String username, String tableId, Integer seatIndex) {
        String sql = seatIndex != null
                ? """
                WITH target AS (
                  SELECT id
                  FROM user_table_sessions
                  WHERE username = ? AND table_id = ? AND seat_index = ? AND left_at IS NULL
                  ORDER BY joined_at DESC
                  LIMIT 1
                )
                UPDATE user_table_sessions
                SET left_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id IN (SELECT id FROM target)
                """
                : """
                WITH target AS (
                  SELECT id
                  FROM user_table_sessions
                  WHERE username = ? AND table_id = ? AND left_at IS NULL
                  ORDER BY joined_at DESC
                  LIMIT 1
                )
                UPDATE user_table_sessions
                SET left_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id IN (SELECT id FROM target)
                """;
        if (seatIndex != null) {
            jdbc.update(sql, username, tableId, seatIndex);
            return;
        }
        jdbc.update(sql, username, tableId);
    }

    /**
     * ユーザーの直近参加履歴を取得します。
     *
     * @param username ユーザー名
     * @param limit 取得件数の上限
     * @return 参加履歴一覧
     */
    @Override
    public List<UserTableHistoryEntry> findRecentByUsername(String username, int limit) {
        return jdbc.query("""
                SELECT username, table_id, table_name, seat_index, visibility, status, flags, joined_at, left_at
                FROM user_table_sessions
                WHERE username = ?
                ORDER BY joined_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapEntry(rs),
                username, limit);
    }

    private UserTableHistoryEntry mapEntry(ResultSet rs) throws SQLException {
        Timestamp leftAt = rs.getTimestamp("left_at");
        return new UserTableHistoryEntry(
                rs.getString("username"),
                rs.getString("table_id"),
                rs.getString("table_name"),
                rs.getInt("seat_index"),
                rs.getString("visibility"),
                rs.getString("status"),
                fromJson(rs.getString("flags")),
                rs.getTimestamp("joined_at").toInstant(),
                leftAt != null ? leftAt.toInstant() : null
        );
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private List<String> fromJson(String json) {
        try {
            return mapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization failed: " + e.getMessage(), e);
        }
    }
}
