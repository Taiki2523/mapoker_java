package com.mapoker.application.table;

import com.mapoker.application.game.GameService;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * テーブル情報の参照を担うサービス。副作用を持たない読み取り専用操作を提供する。
 */
@Service
public class TableQueryService {

    private final TableStore store;
    private final GameService gameService;

    public TableQueryService(TableStore store, GameService gameService) {
        this.store = store;
        this.gameService = gameService;
    }

    public List<TableRecord> listTables() {
        return listTables(null, null);
    }

    public List<TableRecord> listTables(String visibility, String flags) {
        String visibilityFilter = normalizeVisibilityFilter(visibility);
        List<String> requiredFlags = normalizeFlagFilter(flags);

        gameService.listGames().forEach(game -> {
            if (!store.deletedTableIds.contains(game.getId())) {
                fromGame(game);
            }
        });
        return store.tables.values().stream()
                .filter(table -> !"inactive".equals(table.status()))
                .filter(table -> visibilityFilter == null || table.visibility().equals(visibilityFilter))
                .filter(table -> requiredFlags.stream().allMatch(table.flags()::contains))
                .sorted(Comparator.comparing(TableRecord::createdAt).reversed())
                .toList();
    }

    public TableRecord getTable(String id) {
        TableRecord table = store.tables.get(id);
        if (table != null) {
            return fromGame(gameService.getGame(table.gameId()));
        }
        return fromGame(gameService.getGame(id));
    }

    public GameState getTableGame(String id) {
        return gameService.getGame(getTable(id).gameId());
    }

    public List<TableMemberRecord> getMembers(String id) {
        TableRecord table = getTable(id);
        store.tableMembers.putIfAbsent(table.id(), new ArrayList<>());
        return List.copyOf(store.tableMembers.get(table.id()));
    }

    public Integer findSeatIndex(String id, String memberName) {
        if (memberName == null || memberName.isBlank()) {
            return null;
        }
        return getMembers(id).stream()
                .filter(member -> member.name().equals(memberName))
                .map(TableMemberRecord::seatIndex)
                .findFirst()
                .orElse(null);
    }

    TableRecord fromGame(GameState game) {
        TableRecord table = store.tables.compute(game.getId(),
                (ignored, existing) -> mergeTableWithGame(existing, game));
        store.tableMembers.putIfAbsent(table.id(), new ArrayList<>());
        return table;
    }

    private TableRecord mergeTableWithGame(TableRecord existing, GameState game) {
        List<TableMemberRecord> members = store.tableMembers.getOrDefault(game.getId(), List.of());
        int defaultBuyIn = game.getPlayers().isEmpty() ? 0 : game.getPlayers().get(0).getStack();
        return new TableRecord(
                game.getId(),
                existing != null ? existing.roomId() : game.getId(),
                existing != null ? existing.name() : normalizeTableName(null, game.getId()),
                existing != null ? existing.gameType() : "ring",
                Math.max(1, game.getBigBlindSize() / 2),
                game.getBigBlindSize(),
                existing != null ? existing.minBuyIn() : defaultBuyIn,
                existing != null ? existing.maxBuyIn() : defaultBuyIn,
                game.getPlayers().size(),
                existing != null ? existing.flags() : List.of("casual"),
                existing != null ? existing.visibility() : "public",
                deriveStatus(game, existing != null && existing.everSeated(), members),
                existing != null ? existing.gameId() : game.getId(),
                existing != null ? existing.createdAt() : Instant.now(),
                existing != null ? existing.everSeated() : false,
                existing != null ? existing.ante() : game.getAnte(),
                existing != null ? existing.straddleEnabled() : game.isStraddleEnabled()
        );
    }

    private String deriveStatus(GameState game, boolean everSeated, List<TableMemberRecord> members) {
        if (everSeated && members.isEmpty()) {
            return "inactive";
        }
        if (game.getStatus() == null) {
            return "waiting";
        }
        if (game.getPot() == 0 && game.getCommunity().isEmpty()
                && game.getStatus().getLabel().equals("finished")) {
            return "waiting";
        }
        return game.getStatus().getLabel();
    }

    private String normalizeTableName(String tableName, String tableId) {
        if (tableName != null && !tableName.isBlank()) {
            return tableName.trim();
        }
        String suffix = tableId.length() >= 8 ? tableId.substring(0, 8) : tableId;
        return "Cash Orbit " + suffix;
    }

    private String normalizeVisibilityFilter(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return null;
        }
        String normalized = visibility.trim().toLowerCase(Locale.ROOT);
        if ("public".equals(normalized) || "private".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("visibility must be public or private");
    }

    private List<String> normalizeFlagFilter(String flags) {
        if (flags == null || flags.isBlank()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String flag : flags.split(",")) {
            if (flag == null || flag.isBlank()) continue;
            unique.add(flag.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(unique);
    }
}
