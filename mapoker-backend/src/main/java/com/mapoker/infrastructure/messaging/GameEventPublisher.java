package com.mapoker.infrastructure.messaging;

import com.mapoker.application.TableMemberRecord;
import com.mapoker.application.TableService;
import com.mapoker.domain.card.Card;
import com.mapoker.domain.game.GameState;
import com.mapoker.interfaces.http.dto.GameResponse;
import com.mapoker.interfaces.http.dto.TableResponse;
import com.mapoker.interfaces.ws.dto.GameBroadcastPayload;
import com.mapoker.interfaces.ws.dto.HoleCardsPayload;
import com.mapoker.interfaces.ws.dto.MembersBroadcastPayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
public class GameEventPublisher {

    private final SimpMessagingTemplate messaging;
    private final ObjectProvider<TableService> tableServiceProvider;

    public GameEventPublisher(SimpMessagingTemplate messaging,
                              ObjectProvider<TableService> tableServiceProvider) {
        this.messaging = messaging;
        this.tableServiceProvider = tableServiceProvider;
    }

    public void publishGameState(String tableId, GameState state) {
        GameResponse response = GameResponse.from(state, null, false);
        messaging.convertAndSend(
                "/topic/tables/" + tableId + "/game",
                new GameBroadcastPayload(response, null)
        );
    }

    public void publishHoleCards(String tableId, GameState state) {
        try {
            List<TableMemberRecord> members = tableServiceProvider.getObject().getMembers(tableId);
            for (TableMemberRecord member : members) {
                if (member.seatIndex() < 0 || member.seatIndex() >= state.getPlayers().size()) {
                    continue;
                }
                Card[] holeCards = state.getPlayers().get(member.seatIndex()).getHole();
                List<Card> hole = holeCards == null
                        ? List.of()
                        : Arrays.stream(holeCards)
                        .filter(Objects::nonNull)
                        .toList();
                messaging.convertAndSendToUser(
                        member.name(),
                        "/queue/hole-cards",
                        new HoleCardsPayload(tableId, member.seatIndex(), hole)
                );
            }
        } catch (RuntimeException ignored) {
            // Skip user-specific delivery if table/members cannot be resolved.
        }
    }

    public void publishMembers(String tableId, List<TableMemberRecord> members) {
        List<TableResponse.MemberDto> memberDtos = members.stream()
                .map(member -> new TableResponse.MemberDto(
                        member.name(),
                        member.seatIndex(),
                        member.joinedAt(),
                        member.pendingLeave()
                ))
                .toList();
        messaging.convertAndSend(
                "/topic/tables/" + tableId + "/members",
                new MembersBroadcastPayload(tableId, memberDtos)
        );
    }
}
