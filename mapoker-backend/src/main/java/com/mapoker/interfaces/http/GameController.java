package com.mapoker.interfaces.http;

import com.mapoker.application.GameService;
import com.mapoker.application.TableService;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.infrastructure.config.GameProperties;
import com.mapoker.interfaces.http.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

@RestController
@RequestMapping("/v1/games")
public class GameController {

    private final GameService gameService;
    private final GameProperties gameProperties;
    private final TableService tableService;

    public GameController(GameService gameService, GameProperties gameProperties, TableService tableService) {
        this.gameService = gameService;
        this.gameProperties = gameProperties;
        this.tableService = tableService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameResponse createGame(@Valid @RequestBody CreateGameRequest req) {
        List<GameService.PlayerInput> inputs = req.players().stream()
                .map(p -> new GameService.PlayerInput(p.id(), p.stack()))
                .toList();
        OddChipRule oddChipRule = req.oddChipRule() != null ? req.oddChipRule() : gameProperties.defaultOddChipRule();
        GameState state = gameService.createGame(inputs, req.buttonIndex(), req.bigBlind(), req.seed(), oddChipRule);
        return GameResponse.from(state, null, false);
    }

    @GetMapping
    public List<GameResponse> listGames() {
        return gameService.listGames().stream()
                .map(g -> GameResponse.from(g, null, false))
                .toList();
    }

    @GetMapping("/{id}")
    public GameResponse getGame(
            @PathVariable String id,
            @RequestParam(name = "viewer_index", required = false) Integer viewerIndex,
            @RequestParam(name = "spectator", required = false, defaultValue = "0") String spectator,
            @AuthenticationPrincipal UserDetails principal) {
        boolean isSpectator = "1".equals(spectator) || "true".equalsIgnoreCase(spectator);
        Integer effectiveViewerIndex = resolveViewerIndex(id, viewerIndex, principal);
        return GameResponse.from(gameService.getGame(id), effectiveViewerIndex, isSpectator);
    }

    @PostMapping("/{id}/start")
    public GameResponse startHand(@PathVariable String id, @Valid @RequestBody StartHandRequest req) {
        return GameResponse.from(tableService.startHand(id, req.bigBlind()), null, false);
    }

    @PostMapping("/{id}/actions")
    public GameResponse applyAction(
            @PathVariable String id,
            @Valid @RequestBody ApplyActionRequest req,
            @RequestParam(name = "viewer_index", required = false) Integer viewerIndex,
            @AuthenticationPrincipal UserDetails principal) {
        GameState state = gameService.applyAction(id, req.playerIndex(),
                req.action().type(), req.action().amount());
        Integer effectiveViewerIndex = resolveViewerIndex(id, viewerIndex, principal);
        return GameResponse.from(state, effectiveViewerIndex, false);
    }

    @GetMapping("/{id}/actions")
    public ActionsResponse getActions(@PathVariable String id) {
        return ActionsResponse.from(gameService.getActions(id));
    }

    @PostMapping("/{id}/showdown")
    public GameResponse resolveShowdown(@PathVariable String id) {
        gameService.resolveShowdown(id);
        // showdown後のGameResponseにlast_showdownと全参加者のホールカードを含める
        GameState state = gameService.getGame(id);
        return GameResponse.from(state, null, false);
    }

    private Integer resolveViewerIndex(String id, Integer requestedViewerIndex, UserDetails principal) {
        if (principal == null) {
            return requestedViewerIndex;
        }
        return tableService.findSeatIndex(id, principal.getUsername());
    }
}
