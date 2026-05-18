package com.mapoker.application.table;

import com.mapoker.application.game.GameService;
import com.mapoker.application.history.UserTableHistoryService;
import com.mapoker.application.wallet.WalletService;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.infrastructure.config.GameProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * テーブルへの参加・退席・離席確定を担うサービス。
 */
@Service
public class TableMembershipService {

    private final TableStore store;
    private final TableQueryService queryService;
    private final TableEventPublisher eventPublisher;
    private final GameService gameService;
    private final GameProperties gameProperties;
    private final ObjectProvider<WalletService> walletServiceProvider;
    private final UserTableHistoryService userTableHistoryService;
    private final Random rng = new Random();

    public TableMembershipService(TableStore store,
                                  TableQueryService queryService,
                                  TableEventPublisher eventPublisher,
                                  GameService gameService,
                                  GameProperties gameProperties,
                                  ObjectProvider<WalletService> walletServiceProvider,
                                  UserTableHistoryService userTableHistoryService) {
        this.store = store;
        this.queryService = queryService;
        this.eventPublisher = eventPublisher;
        this.gameService = gameService;
        this.gameProperties = gameProperties;
        this.walletServiceProvider = walletServiceProvider;
        this.userTableHistoryService = userTableHistoryService;
    }

    public JoinResult join(String id, String requestedName, int buyIn) {
        return join(id, requestedName, buyIn, requestedName, null, null);
    }

    public JoinResult join(String id, String requestedName, int buyIn, String displayName, String avatarUrl) {
        return join(id, requestedName, buyIn, displayName, avatarUrl, null);
    }

    public JoinResult join(String id, String requestedName, int buyIn,
                           String displayName, String avatarUrl, String publicId) {
        synchronized (store.lock(id)) {
            TableRecord table = queryService.getTable(id);
            String name = normalizeMemberName(requestedName);
            List<TableMemberRecord> members = new ArrayList<>(store.getOrInitMembers(table.id()));

            TableMemberRecord existing = members.stream()
                    .filter(member -> publicId != null
                            ? publicId.equals(member.publicId())
                            : member.name().equals(name))
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                int currentStack = gameService.getSeatStack(table.gameId(), existing.seatIndex());
                GameState state = gameService.getGame(table.gameId());
                boolean handActive = (state.getStatus() == GameStatus.IN_PROGRESS && state.getPot() > 0)
                        || state.getStatus() == GameStatus.SHOWDOWN;

                if (currentStack > 0 && !handActive) {
                    cashOutSeatStackIfPossible(
                            existing.publicId() != null ? existing.publicId() : name,
                            table.gameId(), existing.seatIndex());
                    currentStack = 0;
                }

                if (currentStack == 0 && buyIn > 0) {
                    WalletService walletService = walletServiceProvider.getIfAvailable();
                    if (walletService != null) {
                        if (buyIn < table.minBuyIn() || buyIn > table.maxBuyIn()) {
                            throw new IllegalArgumentException("buy-in out of range");
                        }
                        if (publicId != null) {
                            walletService.rebuy(publicId, table.id(), buyIn);
                        }
                    }
                    gameService.setSeatStack(table.gameId(), existing.seatIndex(), buyIn);
                    gameService.setSittingOut(table.gameId(), existing.seatIndex(), false);
                }

                if (existing.pendingLeave()) {
                    int idx = members.indexOf(existing);
                    members.set(idx, new TableMemberRecord(
                            name, existing.seatIndex(), existing.joinedAt(), false,
                            displayName != null ? displayName : existing.displayName(),
                            avatarUrl != null ? avatarUrl : existing.avatarUrl(),
                            publicId != null ? publicId : existing.publicId()));
                    store.tableMembers.put(table.id(), members);
                }

                store.lastEmptiedAt.remove(table.id());
                userTableHistoryService.recordJoin(name, table, existing.seatIndex());
                JoinResult result = new JoinResult(existing.seatIndex(), List.copyOf(members));
                eventPublisher.publishMembers(table.id(), result.members());
                eventPublisher.publishGameState(table.id(), table.gameId());
                return result;
            }

            int seatIndex = randomAvailableSeat(members, table.maxPlayers());

            WalletService walletService = walletServiceProvider.getIfAvailable();
            if (walletService != null && buyIn > 0 && publicId != null) {
                if (buyIn < table.minBuyIn() || buyIn > table.maxBuyIn()) {
                    throw new IllegalArgumentException("buy-in out of range");
                }
                walletService.buyIn(publicId, table.id(), buyIn);
            }

            GameState state = gameService.getGame(table.gameId());
            boolean handActive = state.getStatus() == GameStatus.IN_PROGRESS && state.getPot() > 0;
            if (handActive) {
                gameService.setSittingOut(table.gameId(), seatIndex, true);
            }
            gameService.setSeatStack(table.gameId(), seatIndex, buyIn);

            store.tables.put(table.id(), new TableRecord(
                    table.id(), table.roomId(), table.name(), table.gameType(),
                    table.smallBlind(), table.bigBlind(), table.minBuyIn(), table.maxBuyIn(),
                    table.maxPlayers(), table.flags(), table.visibility(), table.status(),
                    table.gameId(), table.createdAt(), true, table.ante(), table.straddleEnabled()));

            members.add(new TableMemberRecord(name, seatIndex, Instant.now().toString(),
                    displayName != null ? displayName : name, avatarUrl, publicId));
            members.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
            store.tableMembers.put(table.id(), members);
            store.lastEmptiedAt.remove(table.id());
            userTableHistoryService.recordJoin(name, table, seatIndex);
            JoinResult result = new JoinResult(seatIndex, List.copyOf(members));
            eventPublisher.publishMembers(table.id(), result.members());
            eventPublisher.publishGameState(table.id(), table.gameId());
            return result;
        }
    }

    public List<TableMemberRecord> leave(String id, String name, Integer seatIndex) {
        return leave(id, name, seatIndex, null);
    }

    public List<TableMemberRecord> leave(String id, String name, Integer seatIndex, String publicId) {
        synchronized (store.lock(id)) {
            TableRecord table = queryService.getTable(id);
            List<TableMemberRecord> members = new ArrayList<>(store.getOrInitMembers(table.id()));
            TableMemberRecord member = (publicId != null && !publicId.isBlank())
                    ? members.stream().filter(m -> publicId.equals(m.publicId())).findFirst().orElse(null)
                    : findMember(members, name, seatIndex);

            if (member == null) {
                List<TableMemberRecord> result = List.copyOf(members);
                eventPublisher.publishMembers(table.id(), result);
                return result;
            }

            GameState state = gameService.getGame(table.gameId());
            boolean handActive = (state.getStatus() == GameStatus.IN_PROGRESS && state.getPot() > 0)
                    || state.getStatus() == GameStatus.SHOWDOWN;

            if (handActive) {
                List<TableMemberRecord> updated = new ArrayList<>();
                for (TableMemberRecord current : members) {
                    if (current.name().equals(member.name()) && current.seatIndex() == member.seatIndex()) {
                        updated.add(new TableMemberRecord(current.name(), current.seatIndex(),
                                current.joinedAt(), true, current.displayName(),
                                current.avatarUrl(), current.publicId()));
                    } else {
                        updated.add(current);
                    }
                }
                updated.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
                store.tableMembers.put(table.id(), updated);
                List<TableMemberRecord> result = List.copyOf(updated);
                eventPublisher.publishMembers(table.id(), result);
                return result;
            }

            members.removeIf(c -> c.name().equals(member.name()) && c.seatIndex() == member.seatIndex());
            members.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
            store.tableMembers.put(table.id(), members);
            cashOutSeatStackIfPossible(
                    member.publicId() != null ? member.publicId() : member.name(),
                    table.gameId(), member.seatIndex());
            userTableHistoryService.recordLeave(member.name(), table.id(), member.seatIndex());
            List<TableMemberRecord> result = List.copyOf(members);
            store.trackEmptyTable(table.id(), result);
            eventPublisher.publishMembers(table.id(), result);
            return result;
        }
    }

    public void processPendingLeaves(String tableId) {
        synchronized (store.lock(tableId)) {
            TableRecord table = queryService.getTable(tableId);
            List<TableMemberRecord> members = new ArrayList<>(
                    store.tableMembers.getOrDefault(table.id(), List.of()));
            if (members.isEmpty()) {
                store.tableMembers.putIfAbsent(table.id(), new ArrayList<>());
                return;
            }

            List<TableMemberRecord> remaining = new ArrayList<>();
            for (TableMemberRecord member : members) {
                if (!member.pendingLeave()) {
                    remaining.add(member);
                    continue;
                }
                cashOutSeatStackIfPossible(
                        member.publicId() != null ? member.publicId() : member.name(),
                        table.gameId(), member.seatIndex());
                userTableHistoryService.recordLeave(member.name(), table.id(), member.seatIndex());
            }
            remaining.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
            store.tableMembers.put(table.id(), remaining);
            List<TableMemberRecord> result = List.copyOf(remaining);
            store.trackEmptyTable(tableId, result);
            eventPublisher.publishMembers(tableId, result);
        }
    }

    private void cashOutSeatStackIfPossible(String publicIdOrName, String gameId, int seatIndex) {
        int stack = gameService.getSeatStack(gameId, seatIndex);
        if (stack <= 0) return;
        WalletService walletService = walletServiceProvider.getIfAvailable();
        if (walletService != null && publicIdOrName != null) {
            walletService.cashOut(publicIdOrName, gameId, stack);
        }
        gameService.setSeatStack(gameId, seatIndex, 0);
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
            if (members.stream().noneMatch(m -> m.seatIndex() == seat)) {
                available.add(seat);
            }
        }
        if (available.isEmpty()) {
            throw new IllegalArgumentException("table is full");
        }
        return available.get(rng.nextInt(available.size()));
    }

    private TableMemberRecord findMember(List<TableMemberRecord> members, String name, Integer seatIndex) {
        if (name != null && !name.isBlank()) {
            return members.stream()
                    .filter(m -> m.name().equals(name))
                    .findFirst()
                    .orElse(null);
        }
        if (seatIndex != null) {
            return members.stream()
                    .filter(m -> m.seatIndex() == seatIndex)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /** 着席結果。 */
    public record JoinResult(int assignedSeatIndex, List<TableMemberRecord> members) {}
}
