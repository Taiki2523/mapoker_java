package com.mapoker.interfaces.http;

import com.mapoker.application.table.OfflineSessionService;
import com.mapoker.application.table.OfflineSessionService.SeatResult;
import com.mapoker.interfaces.http.dto.OfflineCashoutRequest;
import com.mapoker.interfaces.http.dto.OfflineSeatRequest;
import com.mapoker.interfaces.http.dto.SessionSummaryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * オフラインセッション固有のエンドポイント（着席・利確・クローズ・サマリー）。
 */
@RestController
@RequestMapping("/v1/games")
public class OfflineController {

    private final OfflineSessionService offlineSession;

    public OfflineController(OfflineSessionService offlineSession) {
        this.offlineSession = offlineSession;
    }

    /**
     * 指定した席番号に着席し、バイイン額を記録する。リバイの場合は cumulative 加算。
     */
    @PostMapping("/{id}/seat")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> seatPlayer(
            @PathVariable String id,
            @Valid @RequestBody OfflineSeatRequest req) {
        SeatResult result = offlineSession.seatPlayer(id, req.seatIndex(), req.name(), req.buyIn(), req.inviteCode());
        return Map.of(
                "seat_index", result.seatIndex(),
                "members", result.members().stream()
                        .map(m -> Map.of(
                                "name", m.name(),
                                "seat_index", m.seatIndex()))
                        .toList());
    }

    /**
     * プレイヤーを利確させ、現在のスタックを cash_out として記録する。
     */
    @PostMapping("/{id}/cashout")
    public Map<String, Object> cashout(
            @PathVariable String id,
            @Valid @RequestBody OfflineCashoutRequest req) {
        int cashOut = offlineSession.cashout(id, req.seatIndex());
        return Map.of("seat_index", req.seatIndex(), "cash_out", cashOut);
    }

    /**
     * セッションをホストが手動クローズする。
     */
    @PostMapping("/{id}/close-session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeSession(@PathVariable String id) {
        offlineSession.closeSession(id);
    }

    /**
     * セッション全体の収支サマリーを返す。
     */
    @GetMapping("/{id}/session-summary")
    public SessionSummaryResponse getSessionSummary(@PathVariable String id) {
        return SessionSummaryResponse.from(id, offlineSession.getSessionSummary(id));
    }
}
