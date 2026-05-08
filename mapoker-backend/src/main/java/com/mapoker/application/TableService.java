package com.mapoker.application;

import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.infrastructure.config.GameProperties;
import com.mapoker.infrastructure.config.WalletProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring の {@code @Service} としてテーブル作成、参加管理、ゲーム連携を担うサービスです。
 */
@Service
public class TableService {

    private final Map<String, TableRecord> tables = new ConcurrentHashMap<>();
    private final Map<String, List<TableMemberRecord>> tableMembers = new ConcurrentHashMap<>();
    private final Map<String, Object> tableLocks = new ConcurrentHashMap<>();

    private final GameService gameService;
    private final GameProperties gameProperties;
    private final WalletProperties walletProperties;
    private final UserTableHistoryService userTableHistoryService;
    private final ObjectProvider<WalletService> walletServiceProvider;
    private final Random rng = new Random();

    public TableService(GameService gameService,
                        GameProperties gameProperties,
                        WalletProperties walletProperties,
                        UserTableHistoryService userTableHistoryService,
                        ObjectProvider<WalletService> walletServiceProvider) {
        this.gameService = gameService;
        this.gameProperties = gameProperties;
        this.walletProperties = walletProperties;
        this.userTableHistoryService = userTableHistoryService;
        this.walletServiceProvider = walletServiceProvider;
    }

    /**
     * リングゲーム用のテーブルを作成します。
     *
     * @param input テーブル作成条件
     * @return 作成されたテーブルとゲーム
     * @throws IllegalArgumentException 入力値が不正な場合
     */
    public CreateTableResult createRingTable(CreateRingTableInput input) {
        validateCreateInput(input);

        List<GameService.PlayerInput> players = new ArrayList<>();
        for (int i = 0; i < input.playerCount(); i++) {
            players.add(new GameService.PlayerInput("p" + (i + 1), 0));
        }

        OddChipRule oddChipRule = gameProperties.defaultOddChipRule();
        GameState game = gameService.createRingGame(players, input.bigBlind(), oddChipRule);

        int minBuyIn = input.bigBlind() * walletProperties.minBuyinBbMultiplier();
        int maxBuyIn = input.bigBlind() * walletProperties.maxBuyinBbMultiplier();

        String normalizedVisibility = normalizeVisibility(input.visibility());
        List<String> flags = normalizeFlags(input.flags());
        TableRecord table = new TableRecord(
                game.getId(),
                game.getId(),
                normalizeTableName(input.tableName(), game.getId()),
                "ring",
                input.smallBlind(),
                input.bigBlind(),
                minBuyIn,
                maxBuyIn,
                input.playerCount(),
                flags,
                normalizedVisibility,
                "inactive",
                game.getId(),
                Instant.now(),
                false
        );
        tables.put(table.id(), table);
        tableMembers.putIfAbsent(table.id(), new ArrayList<>());
        return new CreateTableResult(table, game);
    }

    /**
     * 全テーブルを既定条件で一覧取得します。
     *
     * @return テーブル一覧
     */
    public List<TableRecord> listTables() {
        return listTables(null, null);
    }

    /**
     * 条件に一致するテーブルを一覧取得します。
     *
     * @param visibility 公開設定の絞り込み条件
     * @param flags 必須フラグの絞り込み条件
     * @return 条件に一致するテーブル一覧
     * @throws IllegalArgumentException 公開設定の条件が不正な場合
     */
    public List<TableRecord> listTables(String visibility, String flags) {
        String visibilityFilter = normalizeVisibilityFilter(visibility);
        List<String> requiredFlags = normalizeFlagFilter(flags);

        gameService.listGames().forEach(game -> {
            fromGame(game);
        });
        return tables.values().stream()
                .filter(table -> !"inactive".equals(table.status()))
                .filter(table -> visibilityFilter == null || table.visibility().equals(visibilityFilter))
                .filter(table -> requiredFlags.stream().allMatch(table.flags()::contains))
                .sorted(Comparator.comparing(TableRecord::createdAt).reversed())
                .toList();
    }

    /**
     * 指定 ID のテーブル情報を取得します。
     *
     * @param id テーブル ID
     * @return テーブル情報
     */
    public TableRecord getTable(String id) {
        TableRecord table = tables.get(id);
        if (table != null) {
            return fromGame(gameService.getGame(table.gameId()));
        }
        return fromGame(gameService.getGame(id));
    }

    /**
     * 指定テーブルに紐づくゲーム状態を取得します。
     *
     * @param id テーブル ID
     * @return ゲーム状態
     */
    public GameState getTableGame(String id) {
        return gameService.getGame(getTable(id).gameId());
    }

    /**
     * 指定テーブルの参加者一覧を取得します。
     *
     * @param id テーブル ID
     * @return 参加者一覧
     */
    public List<TableMemberRecord> getMembers(String id) {
        TableRecord table = getTable(id);
        tableMembers.putIfAbsent(table.id(), new ArrayList<>());
        return List.copyOf(tableMembers.get(table.id()));
    }

    /**
     * 指定テーブルで新しいハンドを開始します。
     *
     * @param tableId テーブル ID
     * @param bigBlind ビッグブラインド額
     * @return 更新後のゲーム状態
     */
    public GameState startHand(String tableId, int bigBlind) {
        synchronized (tableLock(tableId)) {
            List<TableMemberRecord> members = getMembers(tableId);
            if (!members.isEmpty()) {
                TableRecord table = getTable(tableId);
                int firstSeat = members.stream()
                        .min(Comparator.comparing(TableMemberRecord::joinedAt))
                        .map(TableMemberRecord::seatIndex)
                        .orElse(0);
                int buttonBefore = (firstSeat - 1 + table.maxPlayers()) % table.maxPlayers();
                gameService.setButtonIndex(tableId, buttonBefore);
            }
            return gameService.startHand(tableId, bigBlind);
        }
    }

    /**
     * 参加者名から着席位置を検索します。
     *
     * @param id テーブル ID
     * @param memberName 参加者名
     * @return 見つかった着席位置。見つからない場合は {@code null}
     */
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

    /**
     * 指定テーブルへ参加者を着席させます。
     *
     * @param id テーブル ID
     * @param requestedName 希望参加者名
     * @param buyIn バイイン額
     * @return 割り当てられた席と最新参加者一覧
     * @throws IllegalArgumentException テーブル満席やバイイン条件違反の場合
     * @throws IllegalStateException ウォレット残高が不足している場合
     */
    public JoinResult join(String id, String requestedName, int buyIn) {
        synchronized (tableLock(id)) {
            TableRecord table = getTable(id);
            String name = normalizeMemberName(requestedName);
            List<TableMemberRecord> members = new ArrayList<>(tableMembers.computeIfAbsent(table.id(), ignored -> new ArrayList<>()));

            TableMemberRecord existing = members.stream()
                    .filter(member -> member.name().equals(name))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                userTableHistoryService.recordJoin(name, table, existing.seatIndex());
                return new JoinResult(existing.seatIndex(), List.copyOf(members));
            }

            int seatIndex = randomAvailableSeat(members, table.maxPlayers());
            GameState state = gameService.getGame(table.gameId());
            boolean handActive = state.getStatus() == GameStatus.IN_PROGRESS && state.getPot() > 0;
            if (handActive) {
                gameService.setSittingOut(table.gameId(), seatIndex, true);
            }

            WalletService walletService = walletServiceProvider.getIfAvailable();
            if (walletService != null && buyIn > 0) {
                if (buyIn < table.minBuyIn() || buyIn > table.maxBuyIn()) {
                    throw new IllegalArgumentException("buy-in out of range");
                }
                walletService.buyIn(name, table.id(), buyIn);
            }
            gameService.setSeatStack(table.gameId(), seatIndex, buyIn);

            tables.put(table.id(), new TableRecord(
                    table.id(),
                    table.roomId(),
                    table.name(),
                    table.gameType(),
                    table.smallBlind(),
                    table.bigBlind(),
                    table.minBuyIn(),
                    table.maxBuyIn(),
                    table.maxPlayers(),
                    table.flags(),
                    table.visibility(),
                    table.status(),
                    table.gameId(),
                    table.createdAt(),
                    true
            ));

            members.add(new TableMemberRecord(name, seatIndex, Instant.now().toString()));
            members.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
            tableMembers.put(table.id(), members);
            userTableHistoryService.recordJoin(name, table, seatIndex);
            return new JoinResult(seatIndex, List.copyOf(members));
        }
    }

    /**
     * 指定テーブルから参加者を離席させます。
     *
     * @param id テーブル ID
     * @param name 参加者名
     * @param seatIndex 着席位置
     * @return 更新後の参加者一覧
     */
    public List<TableMemberRecord> leave(String id, String name, Integer seatIndex) {
        synchronized (tableLock(id)) {
            TableRecord table = getTable(id);
            List<TableMemberRecord> members = new ArrayList<>(tableMembers.computeIfAbsent(table.id(), ignored -> new ArrayList<>()));
            TableMemberRecord member = findMember(members, name, seatIndex);
            if (member == null) {
                return List.copyOf(members);
            }

            GameState state = gameService.getGame(table.gameId());
            boolean handActive = (state.getStatus() == GameStatus.IN_PROGRESS && state.getPot() > 0)
                    || state.getStatus() == GameStatus.SHOWDOWN;
            if (handActive) {
                List<TableMemberRecord> updatedMembers = new ArrayList<>();
                for (TableMemberRecord current : members) {
                    if (current.name().equals(member.name()) && current.seatIndex() == member.seatIndex()) {
                        updatedMembers.add(new TableMemberRecord(
                                current.name(),
                                current.seatIndex(),
                                current.joinedAt(),
                                true
                        ));
                    } else {
                        updatedMembers.add(current);
                    }
                }
                updatedMembers.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
                tableMembers.put(table.id(), updatedMembers);
                return List.copyOf(updatedMembers);
            }

            members.removeIf(current -> current.name().equals(member.name()) && current.seatIndex() == member.seatIndex());
            members.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
            tableMembers.put(table.id(), members);
            cashOutSeatStackIfPossible(member.name(), table.gameId(), member.seatIndex());
            userTableHistoryService.recordLeave(member.name(), table.id(), member.seatIndex());
            return List.copyOf(members);
        }
    }

    /**
     * ハンド終了後に離席待ち参加者を確定処理します。
     *
     * @param tableId テーブル ID
     */
    public void processPendingLeaves(String tableId) {
        synchronized (tableLock(tableId)) {
            TableRecord table = getTable(tableId);
            List<TableMemberRecord> members = new ArrayList<>(tableMembers.getOrDefault(table.id(), List.of()));
            if (members.isEmpty()) {
                tableMembers.putIfAbsent(table.id(), new ArrayList<>());
                return;
            }


            List<TableMemberRecord> remainingMembers = new ArrayList<>();
            for (TableMemberRecord member : members) {
                if (!member.pendingLeave()) {
                    remainingMembers.add(member);
                    continue;
                }
                cashOutSeatStackIfPossible(member.name(), table.gameId(), member.seatIndex());
                userTableHistoryService.recordLeave(member.name(), table.id(), member.seatIndex());
            }
            remainingMembers.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
            List<TableMemberRecord> finalMembers = new ArrayList<>();
            for (TableMemberRecord member : remainingMembers) {
                int stack = gameService.getSeatStack(table.gameId(), member.seatIndex());
                if (stack == 0) {
                    cashOutSeatStackIfPossible(member.name(), table.gameId(), member.seatIndex());
                    userTableHistoryService.recordLeave(member.name(), table.id(), member.seatIndex());
                } else {
                    finalMembers.add(member);
                }
            }
            finalMembers.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
            tableMembers.put(table.id(), finalMembers);
        }
    }

    private Object tableLock(String tableId) {
        return tableLocks.computeIfAbsent(tableId, ignored -> new Object());
    }

    private TableRecord fromGame(GameState game) {
        TableRecord table = tables.compute(game.getId(), (ignored, existing) -> mergeTableWithGame(existing, game));
        tableMembers.putIfAbsent(table.id(), new ArrayList<>());
        return table;
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

    private List<String> normalizeFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return List.of("casual");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String flag : flags) {
            if (flag == null || flag.isBlank()) {
                continue;
            }
            unique.add(flag.trim().toLowerCase());
        }
        return unique.isEmpty() ? List.of("casual") : List.copyOf(unique);
    }

    private List<String> normalizeFlagFilter(String flags) {
        if (flags == null || flags.isBlank()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String flag : flags.split(",")) {
            if (flag == null || flag.isBlank()) {
                continue;
            }
            unique.add(flag.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(unique);
    }

    private String normalizeMemberName(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return gameProperties.defaultPlayerName();
        }
        return requestedName.trim();
    }

    private int randomAvailableSeat(List<TableMemberRecord> members, int maxPlayers) {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < maxPlayers; i++) {
            final int seat = i;
            if (members.stream().noneMatch(member -> member.seatIndex() == seat)) {
                available.add(seat);
            }
        }
        if (available.isEmpty()) {
            throw new IllegalArgumentException("table is full");
        }
        return available.get(rng.nextInt(available.size()));
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

    private TableMemberRecord findMember(List<TableMemberRecord> members, String name, Integer seatIndex) {
        if (name != null && !name.isBlank()) {
            return members.stream()
                    .filter(member -> member.name().equals(name))
                    .findFirst()
                    .orElse(null);
        }
        if (seatIndex != null) {
            return members.stream()
                    .filter(member -> member.seatIndex() == seatIndex)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void cashOutSeatStackIfPossible(String name, String tableId, int seatIndex) {
        int stack = gameService.getSeatStack(tableId, seatIndex);
        if (stack <= 0) {
            return;
        }

        WalletService walletService = walletServiceProvider.getIfAvailable();
        if (walletService != null) {
            walletService.cashOut(name, tableId, stack);
        }
        gameService.setSeatStack(tableId, seatIndex, 0);
    }

    private TableRecord mergeTableWithGame(TableRecord existing, GameState game) {
        List<TableMemberRecord> members = tableMembers.getOrDefault(game.getId(), List.of());
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
                existing != null ? existing.everSeated() : false
        );
    }

    private String deriveStatus(GameState game, boolean everSeated, List<TableMemberRecord> members) {
        if (everSeated && members.isEmpty()) {
            return "inactive";
        }
        if (game.getStatus() == null) {
            return "waiting";
        }
        if (game.getPot() == 0 && game.getCommunity().isEmpty() && game.getStatus().getLabel().equals("finished")) {
            return "waiting";
        }
        return game.getStatus().getLabel();
    }

    /**
     * リングテーブル作成時の入力値です。
     *
     * @param tableName テーブル名
     * @param playerCount プレイヤー人数
     * @param smallBlind スモールブラインド額
     * @param bigBlind ビッグブラインド額
     * @param visibility 公開設定
     * @param flags テーブル属性の一覧
     */
    public record CreateRingTableInput(
            String tableName,
            int playerCount,
            int smallBlind,
            int bigBlind,
            String visibility,
            List<String> flags
    ) {
        public CreateRingTableInput(String tableName,
                                    int playerCount,
                                    int bigBlind,
                                    String visibility,
                                    List<String> flags) {
            this(tableName, playerCount, Math.max(1, bigBlind / 2), bigBlind, visibility, flags);
        }
    }

    /**
     * 着席結果を返すレコードです。
     *
     * @param assignedSeatIndex 割り当てられた席番号
     * @param members 更新後の参加者一覧
     */
    public record JoinResult(int assignedSeatIndex, List<TableMemberRecord> members) {}

    /**
     * テーブル作成結果を返すレコードです。
     *
     * @param table 作成されたテーブル
     * @param game 作成されたゲーム
     */
    public record CreateTableResult(TableRecord table, GameState game) {}
}
