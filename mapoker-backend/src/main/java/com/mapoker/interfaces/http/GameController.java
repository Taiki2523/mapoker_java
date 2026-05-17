package com.mapoker.interfaces.http;

import com.mapoker.application.GameService;
import com.mapoker.application.TableService;
import com.mapoker.application.UserService;
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

/**
 * ゲーム操作エンドポイントを提供するコントローラー。
 *
 * <p>Texas Hold'em ポーカーゲームのライフサイクル（作成・アクション適用・ショーダウン）を
 * REST API として公開する。状態はすべてサーバーサイドで管理され、クライアントはゲームIDを
 * 介してステートレスにアクセスする。
 *
 * <p>ホールカードの可視性はバックエンドで制御される。{@code viewer_index} クエリパラメータで
 * 指定されたプレイヤーのカードのみ返し、ショーダウン・終了時は全員のカードを公開する。
 * 認証済みユーザーの場合、テーブルシートインデックスを自動解決して適切なカードを返す。
 */
@RestController
@RequestMapping("/v1/games")
public class GameController {

    private final GameService gameService;
    private final GameProperties gameProperties;
    private final TableService tableService;
    private final UserService userService;

    public GameController(GameService gameService, GameProperties gameProperties,
                          TableService tableService, UserService userService) {
        this.gameService = gameService;
        this.gameProperties = gameProperties;
        this.tableService = tableService;
        this.userService = userService;
    }

    /**
     * 新しいゲームを作成する。
     *
     * <p>{@code odd_chip_rule} が省略された場合は {@link GameProperties#defaultOddChipRule()} が適用される。
     * {@code seed} を指定するとデッキシャッフルの再現性が保証され、テストに利用できる。
     *
     * @param req プレイヤー情報・ボタン位置・ブラインド額・オプションのシードを含むリクエスト
     * @return 作成されたゲームの {@link GameResponse}（HTTP 201）
     */
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

    /**
     * 全ゲームの一覧を返す。
     *
     * <p>ホールカードは一切含まれない（{@code viewerIndex=null, spectator=false}）。
     *
     * @return 全ゲームの {@link GameResponse} リスト
     */
    @GetMapping
    public List<GameResponse> listGames() {
        return gameService.listGames().stream()
                .map(g -> GameResponse.from(g, null, false))
                .toList();
    }

    /**
     * 指定IDのゲーム状態を返す。
     *
     * <p>認証済みユーザーの場合、テーブルメンバーシップからシートインデックスを自動解決し、
     * 自分のホールカードのみを含めて返す。{@code spectator=1} を指定するとホールカードを
     * 一切含まない観戦モードになる。
     *
     * @param id          ゲームID
     * @param viewerIndex 表示するプレイヤーのインデックス（認証済み時は自動解決されるため通常不要）
     * @param spectator   {@code "1"} または {@code "true"} を指定するとホールカードを非表示にする
     * @param principal   Spring Security が注入する認証済みユーザー詳細。未認証時は {@code null}
     * @return ゲームの {@link GameResponse}
     */
    @GetMapping("/{id}")
    public GameResponse getGame(
            @PathVariable String id,
            @RequestParam(name = "viewer_index", required = false) Integer viewerIndex,
            @RequestParam(name = "spectator", required = false, defaultValue = "0") String spectator,
            @AuthenticationPrincipal UserDetails principal) {
        boolean isSpectator = "1".equals(spectator) || "true".equalsIgnoreCase(spectator);
        Integer effectiveViewerIndex = resolveViewerIndex(id, viewerIndex, principal);
        return GameResponse.from(gameService.getGame(id), effectiveViewerIndex, isSpectator, seatedCount(id));
    }

    /**
     * 新しいハンドを開始する。
     *
     * <p>ゲームの状態が {@code finished} であることが前提。ブラインド額はハンドごとに変更可能。
     *
     * @param id  ゲームID
     * @param req 新しいビッグブラインド額を含むリクエスト
     * @return ハンド開始後のゲームの {@link GameResponse}
     */
    @PostMapping("/{id}/start")
    public GameResponse startHand(@PathVariable String id, @Valid @RequestBody StartHandRequest req) {
        return GameResponse.from(tableService.startHand(id, req.bigBlind(), req.doStraddle()), null, false, seatedCount(id));
    }

    /**
     * プレイヤーのアクションを適用する。
     *
     * <p>{@code bet}/{@code raise} の {@code amount} は増分ではなく合計額（raise-to total）を指定する。
     * {@code call} は {@code amount=0} で自動コールとなる。
     * ミニマムレイズはドメイン層で検証され、違反時は {@link IllegalArgumentException} がスローされる。
     *
     * @param id          ゲームID
     * @param req         プレイヤーインデックスとアクション内容を含むリクエスト
     * @param viewerIndex 表示するプレイヤーのインデックス（認証済み時は自動解決）
     * @param principal   Spring Security が注入する認証済みユーザー詳細。未認証時は {@code null}
     * @return アクション適用後のゲームの {@link GameResponse}
     */
    @PostMapping("/{id}/actions")
    public GameResponse applyAction(
            @PathVariable String id,
            @Valid @RequestBody ApplyActionRequest req,
            @RequestParam(name = "viewer_index", required = false) Integer viewerIndex,
            @AuthenticationPrincipal UserDetails principal) {
        GameState state = gameService.applyAction(id, req.playerIndex(),
                req.action().type(), req.action().amount());
        Integer effectiveViewerIndex = resolveViewerIndex(id, viewerIndex, principal);
        return GameResponse.from(state, effectiveViewerIndex, false, seatedCount(id));
    }

    /**
     * 指定ゲームのアクション履歴を返す。
     *
     * @param id ゲームID
     * @return アクション履歴の {@link ActionsResponse}
     */
    @GetMapping("/{id}/actions")
    public ActionsResponse getActions(@PathVariable String id) {
        return ActionsResponse.from(gameService.getActions(id));
    }

    /**
     * ショーダウンを解決してポットを分配する。
     *
     * <p>ショーダウン後は全プレイヤーのホールカードが公開される（{@code viewerIndex=null}）。
     * レスポンスには {@code last_showdown} フィールドに勝者・最強ハンド・支払い額が含まれる。
     *
     * @param id ゲームID
     * @return ショーダウン解決後のゲームの {@link GameResponse}
     */
    @PostMapping("/{id}/showdown")
    public GameResponse resolveShowdown(@PathVariable String id) {
        gameService.resolveShowdown(id);
        // showdown後のGameResponseにlast_showdownと全参加者のホールカードを含める
        GameState state = gameService.getGame(id);
        return GameResponse.from(state, null, false, seatedCount(id));
    }

    /**
     * 有効な {@code viewerIndex} を解決する内部ヘルパー。
     *
     * <p>認証済みユーザーの場合はテーブルメンバーシップからシートインデックスを取得し、
     * リクエストパラメータを上書きする。未認証時はリクエストパラメータの値をそのまま返す。
     *
     * @param id                    ゲームID
     * @param requestedViewerIndex  クライアントが指定した {@code viewer_index}
     * @param principal             認証済みユーザー詳細。未認証時は {@code null}
     * @return 有効な {@code viewerIndex}、または {@code null}（観戦モード相当）
     */
    /** テーブルの着席中プレイヤー数（pendingLeave 除く）を返す。テーブルが存在しない場合は -1。 */
    private int seatedCount(String id) {
        try {
            return (int) tableService.getMembers(id).stream()
                    .filter(m -> !m.pendingLeave())
                    .count();
        } catch (Exception e) {
            return -1;
        }
    }

    private Integer resolveViewerIndex(String id, Integer requestedViewerIndex, UserDetails principal) {
        if (principal == null) {
            return requestedViewerIndex;
        }
        try {
            String username = userService.getByPublicId(principal.getUsername()).username();
            return tableService.findSeatIndex(id, username);
        } catch (Exception ignored) {
            return requestedViewerIndex;
        }
    }
}
