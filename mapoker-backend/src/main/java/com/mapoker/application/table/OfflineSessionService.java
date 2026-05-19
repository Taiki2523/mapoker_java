package com.mapoker.application.table;

import com.mapoker.application.game.GameLifecycleService;
import com.mapoker.application.game.GameReadService;
import com.mapoker.application.ports.OfflinePlayerRepository;
import com.mapoker.application.ports.OfflinePlayerRepository.OfflinePlayerData;
import com.mapoker.domain.game.GameState;
import com.mapoker.infrastructure.messaging.GameEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * オフラインセッション固有の操作（着席・利確・サマリー）を担うサービス。
 * ウォレット操作は一切行わない。
 */
@Service
public class OfflineSessionService {

    private final TableStore store;
    private final TableQueryService queryService;
    private final GameLifecycleService gameLifecycle;
    private final GameReadService gameRead;
    private final OfflinePlayerRepository offlineRepo;
    private final ObjectProvider<GameEventPublisher> eventPublisherProvider;
    private final TableEventPublisher tableEventPublisher;

    public OfflineSessionService(TableStore store,
                                 TableQueryService queryService,
                                 GameLifecycleService gameLifecycle,
                                 GameReadService gameRead,
                                 OfflinePlayerRepository offlineRepo,
                                 ObjectProvider<GameEventPublisher> eventPublisherProvider,
                                 TableEventPublisher tableEventPublisher) {
        this.store = store;
        this.queryService = queryService;
        this.gameLifecycle = gameLifecycle;
        this.gameRead = gameRead;
        this.offlineRepo = offlineRepo;
        this.eventPublisherProvider = eventPublisherProvider;
        this.tableEventPublisher = tableEventPublisher;
    }

    /**
     * 指定席にプレイヤーを着席させ、バイイン額を記録する。
     * 既存プレイヤーの場合は cumulative（リバイ）として加算する。
     */
    public SeatResult seatPlayer(String tableId, int seatIndex, String name, int buyIn) {
        synchronized (store.lock(tableId)) {
            TableRecord table = queryService.getTable(tableId);
            if (!"offline".equals(table.gameType())) {
                throw new IllegalStateException("seatPlayer is only available for offline tables");
            }

            List<TableMemberRecord> members = new ArrayList<>(store.getOrInitMembers(table.id()));

            boolean seatTaken = members.stream()
                    .anyMatch(m -> m.seatIndex() == seatIndex && !m.name().equals(name));
            if (seatTaken) {
                throw new IllegalArgumentException("seat " + seatIndex + " is already taken");
            }

            boolean isRebuy = members.stream()
                    .anyMatch(m -> m.name().equals(name) && m.seatIndex() == seatIndex);

            if (!isRebuy) {
                members.add(new TableMemberRecord(name, seatIndex, Instant.now().toString(),
                        name, null, null));
                members.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
                store.tableMembers.put(table.id(), members);
                store.tables.put(table.id(), new TableRecord(
                        table.id(), table.roomId(), table.name(), table.gameType(),
                        table.smallBlind(), table.bigBlind(), table.minBuyIn(), table.maxBuyIn(),
                        table.maxPlayers(), table.flags(), table.visibility(), table.status(),
                        table.gameId(), table.createdAt(), true, table.ante(), table.straddleEnabled()));
            }

            int currentStack = gameLifecycle.getSeatStack(table.gameId(), seatIndex);
            gameLifecycle.setSeatStack(table.gameId(), seatIndex, currentStack + buyIn);
            offlineRepo.addBuyIn(table.gameId(), seatIndex, buyIn);

            tableEventPublisher.publishMembers(table.id(), List.copyOf(members));
            tableEventPublisher.publishGameState(table.id(), table.gameId());

            return new SeatResult(seatIndex, List.copyOf(members));
        }
    }

    /**
     * プレイヤーを利確させ、現在のスタックを cash_out として記録する。
     */
    public int cashout(String tableId, int seatIndex) {
        synchronized (store.lock(tableId)) {
            TableRecord table = queryService.getTable(tableId);
            if (!"offline".equals(table.gameType())) {
                throw new IllegalStateException("cashout is only available for offline tables");
            }

            int stack = gameLifecycle.getSeatStack(table.gameId(), seatIndex);
            offlineRepo.setCashout(table.gameId(), seatIndex, stack, Instant.now());
            gameLifecycle.setSeatStack(table.gameId(), seatIndex, 0);

            List<TableMemberRecord> members = new ArrayList<>(
                    store.getOrInitMembers(table.id()));
            members.removeIf(m -> m.seatIndex() == seatIndex);
            members.sort(Comparator.comparingInt(TableMemberRecord::seatIndex));
            store.tableMembers.put(table.id(), members);
            store.trackEmptyTable(table.id(), members);

            tableEventPublisher.publishMembers(table.id(), List.copyOf(members));
            tableEventPublisher.publishGameState(table.id(), table.gameId());

            return stack;
        }
    }

    /**
     * セッションをクローズする（全員利確済みとして扱う）。
     */
    public void closeSession(String tableId) {
        synchronized (store.lock(tableId)) {
            TableRecord table = queryService.getTable(tableId);
            if (!"offline".equals(table.gameType())) {
                throw new IllegalStateException("closeSession is only available for offline tables");
            }
            store.tableMembers.put(table.id(), new ArrayList<>());
            store.trackEmptyTable(table.id(), List.of());
            tableEventPublisher.publishMembers(table.id(), List.of());
        }
    }

    /**
     * セッション全体の収支サマリーを返す。
     */
    public List<PlayerSummary> getSessionSummary(String tableId) {
        TableRecord table = queryService.getTable(tableId);
        if (!"offline".equals(table.gameType())) {
            throw new IllegalStateException("session summary is only available for offline tables");
        }

        List<OfflinePlayerData> offlineData = offlineRepo.findByGameId(table.gameId());
        GameState state = gameRead.getGame(table.gameId());
        List<TableMemberRecord> members = store.getOrInitMembers(table.id());

        List<PlayerSummary> summaries = new ArrayList<>();
        for (OfflinePlayerData od : offlineData) {
            if (od.offlineBuyIn() == 0) continue;
            int currentStack = state.getPlayers().get(od.seatIndex()).getStack();
            Integer cashOut = od.offlineCashOut() != null ? od.offlineCashOut() : currentStack;
            int profit = cashOut - od.offlineBuyIn();
            String displayName = members.stream()
                    .filter(m -> m.seatIndex() == od.seatIndex())
                    .map(TableMemberRecord::displayName)
                    .findFirst()
                    .orElse("Seat " + (od.seatIndex() + 1));
            summaries.add(new PlayerSummary(
                    od.seatIndex(), displayName,
                    od.offlineBuyIn(), cashOut, profit,
                    od.cashoutAt() != null));
        }
        summaries.sort(Comparator.comparingInt(PlayerSummary::seatIndex));
        return summaries;
    }

    public record SeatResult(int seatIndex, List<TableMemberRecord> members) {}

    public record PlayerSummary(
            int seatIndex,
            String displayName,
            int buyIn,
            int cashOut,
            int profit,
            boolean settled
    ) {}
}
