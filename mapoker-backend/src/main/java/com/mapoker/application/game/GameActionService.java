package com.mapoker.application.game;

import com.mapoker.application.history.HandHistoryEntry;
import com.mapoker.application.history.HandHistoryService;
import com.mapoker.application.ports.GameRepository;
import com.mapoker.application.table.TableMemberRecord;
import com.mapoker.application.table.TableMembershipService;
import com.mapoker.application.table.TableQueryService;
import com.mapoker.domain.card.Card;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.Player;
import com.mapoker.domain.game.ShowdownResult;
import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionType;
import com.mapoker.domain.rules.Street;
import com.mapoker.infrastructure.messaging.GameEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * プレイヤーアクションとショーダウン解決を担うサービス。
 * アクション適用後のハンド履歴記録・離席確定・イベント発行も責務とする。
 */
@Service
public class GameActionService {

    private static final String MASKED_HOLE_CARD = "??";

    private final GameRepository gameRepository;
    private final GameReadService readService;
    private final HandHistoryService handHistoryService;
    private final ObjectProvider<TableMembershipService> tableMembershipProvider;
    private final ObjectProvider<TableQueryService> tableQueryProvider;
    private final ObjectProvider<GameEventPublisher> eventPublisherProvider;

    public GameActionService(GameRepository gameRepository,
                             GameReadService readService,
                             HandHistoryService handHistoryService,
                             ObjectProvider<TableMembershipService> tableMembershipProvider,
                             ObjectProvider<TableQueryService> tableQueryProvider,
                             ObjectProvider<GameEventPublisher> eventPublisherProvider) {
        this.gameRepository = gameRepository;
        this.readService = readService;
        this.handHistoryService = handHistoryService;
        this.tableMembershipProvider = tableMembershipProvider;
        this.tableQueryProvider = tableQueryProvider;
        this.eventPublisherProvider = eventPublisherProvider;
    }

    public GameState applyAction(String id, int playerIndex, ActionType type, int amount) {
        GameState state = readService.getGame(id);
        Street streetBefore = state.getStreet();
        state.applyAction(playerIndex, Action.of(type, amount));
        Instant streetRevealedAt = state.getStreet() != streetBefore ? Instant.now() : null;
        ActionRecord record = new ActionRecord(
                gameRepository.findActionsByGameId(id).size() + 1,
                playerIndex, type, amount);
        gameRepository.update(id, state, record);
        publishGame(id, state, streetRevealedAt);
        recordHandHistoryIfFinished(id, state);
        if (state.isFoldWin()) {
            TableMembershipService membership = tableMembershipProvider.getIfAvailable();
            if (membership != null) {
                membership.processPendingLeaves(id);
            }
        }
        return state;
    }

    public ShowdownResult resolveShowdown(String id) {
        GameState state = readService.getGame(id);
        ShowdownResult result = state.resolveShowdown();
        state.applyPayouts(result.payouts());
        gameRepository.update(id, state);

        List<ActionRecord> existing = gameRepository.findActionsByGameId(id);
        boolean alreadyRecorded = existing.stream()
                .anyMatch(a -> a.actionType() == ActionType.SHOWDOWN || a.actionType() == ActionType.PAYOUT);
        if (!alreadyRecorded) {
            int nextSeq = existing.size() + 1;
            boolean isFoldWin = result.bestHand() == null || result.bestHand().rank() == null;
            String handLabel = isFoldWin ? "fold_win" : result.bestHand().rank().getLabel();
            for (int winnerIdx : result.winnerIndexes()) {
                gameRepository.appendAction(id, new ActionRecord(nextSeq++, winnerIdx,
                        ActionType.SHOWDOWN, 0, handLabel));
            }
            List<Integer> payouts = result.payouts();
            for (int i = 0; i < payouts.size(); i++) {
                if (payouts.get(i) > 0) {
                    gameRepository.appendAction(id, new ActionRecord(nextSeq++, i,
                            ActionType.PAYOUT, payouts.get(i), null));
                }
            }
        }

        recordHandHistoryIfFinished(id, state);
        TableMembershipService membership = tableMembershipProvider.getIfAvailable();
        if (membership != null) {
            membership.processPendingLeaves(id);
        }
        publishGame(id, readService.getGame(id), null);
        return result;
    }

    private void publishGame(String tableId, GameState state, Instant streetRevealedAt) {
        GameEventPublisher pub = eventPublisherProvider.getIfAvailable();
        if (pub != null) {
            pub.publishGameState(tableId, state, streetRevealedAt);
        }
    }

    private void recordHandHistoryIfFinished(String tableId, GameState state) {
        if (state.getStatus() != GameStatus.FINISHED || state.getLastShowdown() == null) {
            return;
        }
        handHistoryService.record(buildHandHistoryEntry(tableId, state));
    }

    private HandHistoryEntry buildHandHistoryEntry(String tableId, GameState state) {
        List<Integer> payouts = state.getLastShowdown().payouts();
        List<HandHistoryEntry.PlayerSnapshot> players = new ArrayList<>();
        List<TableMemberRecord> members = lookupMembers(tableId);
        for (int seatIndex = 0; seatIndex < state.getPlayers().size(); seatIndex++) {
            Player player = state.getPlayers().get(seatIndex);
            int payout = seatIndex < payouts.size() ? payouts.get(seatIndex) : 0;
            int stackBefore = player.getStack() + player.getTotalContrib() - payout;
            players.add(new HandHistoryEntry.PlayerSnapshot(
                    resolvePlayerName(members, seatIndex, player.getId()),
                    seatIndex, stackBefore, player.getStack(),
                    player.isFolded(), maskHoleCards(player.getHole())));
        }
        return new HandHistoryEntry(tableId, UUID.randomUUID().toString(), players,
                state.getLastShowdown().winnerIndexes(),
                payouts.stream().mapToInt(Integer::intValue).sum(),
                state.getStreet().getLabel(), Instant.now());
    }

    private List<TableMemberRecord> lookupMembers(String tableId) {
        TableQueryService queryService = tableQueryProvider.getIfAvailable();
        if (queryService == null) return List.of();
        try {
            return queryService.getMembers(tableId);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private String resolvePlayerName(List<TableMemberRecord> members, int seatIndex, String fallback) {
        return members.stream()
                .filter(m -> m.seatIndex() == seatIndex)
                .map(TableMemberRecord::name)
                .findFirst()
                .orElseGet(() -> (fallback != null && !fallback.isBlank())
                        ? fallback : "Seat " + (seatIndex + 1));
    }

    private List<String> maskHoleCards(Card[] hole) {
        if (hole == null) return List.of();
        List<String> masked = new ArrayList<>();
        for (Card card : hole) {
            if (card != null) masked.add(MASKED_HOLE_CARD);
        }
        return List.copyOf(masked);
    }
}
