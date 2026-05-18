package com.mapoker.application.table;

import com.mapoker.application.game.GameService;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.infrastructure.config.GameProperties;
import com.mapoker.infrastructure.config.WalletProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * テーブル作成・ハンド進行・スケジュールクリーンアップを担うサービス。
 */
@Service
public class TableLifecycleService {

    private final TableStore store;
    private final TableQueryService queryService;
    private final GameService gameService;
    private final GameProperties gameProperties;
    private final WalletProperties walletProperties;

    public TableLifecycleService(TableStore store,
                                 TableQueryService queryService,
                                 GameService gameService,
                                 GameProperties gameProperties,
                                 WalletProperties walletProperties) {
        this.store = store;
        this.queryService = queryService;
        this.gameService = gameService;
        this.gameProperties = gameProperties;
        this.walletProperties = walletProperties;
    }

    public CreateTableResult createRingTable(CreateRingTableInput input) {
        validateCreateInput(input);

        List<GameService.PlayerInput> players = new ArrayList<>();
        for (int i = 0; i < input.playerCount(); i++) {
            players.add(new GameService.PlayerInput("p" + (i + 1), 0));
        }

        OddChipRule oddChipRule = gameProperties.defaultOddChipRule();
        GameState game = gameService.createRingGame(
                players, input.bigBlind(), oddChipRule, input.ante(), input.straddleEnabled());

        int minBuyIn = input.bigBlind() * walletProperties.minBuyinBbMultiplier();
        int maxBuyIn = input.bigBlind() * walletProperties.maxBuyinBbMultiplier();

        TableRecord table = new TableRecord(
                game.getId(), game.getId(),
                normalizeTableName(input.tableName(), game.getId()),
                "ring",
                input.smallBlind(), input.bigBlind(),
                minBuyIn, maxBuyIn, input.playerCount(),
                normalizeFlags(input.flags()),
                normalizeVisibility(input.visibility()),
                "inactive",
                game.getId(),
                Instant.now(),
                false,
                input.ante(),
                input.straddleEnabled());

        store.tables.put(table.id(), table);
        store.tableMembers.putIfAbsent(table.id(), new ArrayList<>());
        store.lastEmptiedAt.put(table.id(), Instant.now());
        return new CreateTableResult(table, game);
    }

    public GameState startHand(String tableId, int bigBlind) {
        return startHand(tableId, bigBlind, false);
    }

    public GameState startHand(String tableId, int bigBlind, boolean doStraddle) {
        synchronized (store.lock(tableId)) {
            GameState current = gameService.getGame(tableId);
            if (current.getStatus() == GameStatus.IN_PROGRESS) {
                return current;
            }
            long activeRosterCount = queryService.getMembers(tableId).stream()
                    .filter(m -> !m.pendingLeave())
                    .count();
            if (activeRosterCount < com.mapoker.domain.PokerConstants.MIN_PLAYERS) {
                throw new IllegalStateException("not enough players at the table");
            }
            return gameService.startHand(tableId, bigBlind, doStraddle);
        }
    }

    public void setStraddleIntent(String tableId, boolean straddle) {
        synchronized (store.lock(tableId)) {
            gameService.setStraddleIntent(tableId, straddle);
        }
    }

    @Scheduled(fixedDelay = 600_000)
    public void removeStaleEmptyTables() {
        Instant threshold = Instant.now().minus(1, ChronoUnit.HOURS);
        store.lastEmptiedAt.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(threshold)) {
                String tableId = entry.getKey();
                store.deletedTableIds.add(tableId);
                store.tables.remove(tableId);
                store.tableMembers.remove(tableId);
                return true;
            }
            return false;
        });
    }

    private void validateCreateInput(CreateRingTableInput input) {
        if (input.playerCount() < 2 || input.playerCount() > 9) {
            throw new IllegalArgumentException("player count must be between 2 and 9");
        }
        if (input.bigBlind() <= 0) {
            throw new IllegalArgumentException("big blind must be positive");
        }
        if (input.smallBlind() <= 0) {
            throw new IllegalArgumentException("small blind must be positive");
        }
    }

    private String normalizeTableName(String tableName, String tableId) {
        if (tableName != null && !tableName.isBlank()) {
            return tableName.trim();
        }
        String suffix = tableId.length() >= 8 ? tableId.substring(0, 8) : tableId;
        return "Cash Orbit " + suffix;
    }

    private String normalizeVisibility(String visibility) {
        if ("private".equalsIgnoreCase(visibility)) {
            return "private";
        }
        return "public";
    }

    private List<String> normalizeFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return List.of("casual");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String flag : flags) {
            if (flag == null || flag.isBlank()) continue;
            unique.add(flag.trim().toLowerCase());
        }
        return unique.isEmpty() ? List.of("casual") : List.copyOf(unique);
    }

    /** リングテーブル作成時の入力値。 */
    public record CreateRingTableInput(
            String tableName,
            int playerCount,
            int smallBlind,
            int bigBlind,
            String visibility,
            List<String> flags,
            int ante,
            boolean straddleEnabled
    ) {
        public CreateRingTableInput(String tableName, int playerCount, int bigBlind,
                                    String visibility, List<String> flags) {
            this(tableName, playerCount, Math.max(1, bigBlind / 2), bigBlind, visibility, flags, 0, false);
        }

        public CreateRingTableInput(String tableName, int playerCount, int smallBlind,
                                    int bigBlind, String visibility, List<String> flags) {
            this(tableName, playerCount, smallBlind, bigBlind, visibility, flags, 0, false);
        }

        public CreateRingTableInput(String tableName, int playerCount, int smallBlind,
                                    int bigBlind, String visibility, List<String> flags, int ante) {
            this(tableName, playerCount, smallBlind, bigBlind, visibility, flags, ante, false);
        }
    }

    /** テーブル作成結果。 */
    public record CreateTableResult(TableRecord table, GameState game) {}
}
