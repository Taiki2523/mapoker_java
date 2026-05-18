package com.mapoker.application.table;

import com.mapoker.application.game.GameReadService;
import com.mapoker.infrastructure.messaging.GameEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/** テーブル関連の WebSocket イベントを発行するコンポーネント。 */
@Component
public class TableEventPublisher {

    private final ObjectProvider<GameEventPublisher> eventPublisherProvider;
    private final GameReadService gameService;

    public TableEventPublisher(ObjectProvider<GameEventPublisher> eventPublisherProvider,
                               GameReadService gameService) {
        this.eventPublisherProvider = eventPublisherProvider;
        this.gameService = gameService;
    }

    public void publishMembers(String tableId, List<TableMemberRecord> members) {
        GameEventPublisher pub = eventPublisherProvider.getIfAvailable();
        if (pub != null) {
            pub.publishMembers(tableId, members);
        }
    }

    public void publishGameState(String tableId, String gameId) {
        GameEventPublisher pub = eventPublisherProvider.getIfAvailable();
        if (pub != null) {
            pub.publishGameState(tableId, gameService.getGame(gameId));
        }
    }
}
