package com.mapoker.application;

import com.mapoker.domain.card.Card;
import com.mapoker.domain.game.GameState;
import com.mapoker.domain.game.GameStatus;
import com.mapoker.domain.game.OddChipRule;
import com.mapoker.domain.game.Player;
import com.mapoker.domain.game.ShowdownResult;
import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionType;
import com.mapoker.infrastructure.messaging.GameEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;

/**
 * ゲームのライフサイクル管理を担うアプリケーションサービス。
 *
 * <p>ゲームの作成・アクション適用・ショーダウン解決を責務とする。
 * ドメイン層（{@link com.mapoker.domain.game.GameState}）への操作は
 * すべてこのクラスを経由する。
 *
 * <p>ハンド終了時には自動的に {@link HandHistoryService#record} を呼び出す。
 * また fold-win / showdown 解決後は {@link TableService#processPendingLeaves} を
 * トリガーして離席待ちプレイヤーを処理する。
 *
 * <p>循環依存を避けるため {@link TableService} は {@link ObjectProvider} 経由で取得する。
 */
@Service
public class GameService {

    private static final String MASKED_HOLE_CARD = "??";

    private final GameRepository gameRepository;
    private final HandHistoryService handHistoryService;
    private final ObjectProvider<TableService> tableServiceProvider;
    private final ObjectProvider<GameEventPublisher> eventPublisherProvider;

    public GameService(GameRepository gameRepository,
                       HandHistoryService handHistoryService,
                       ObjectProvider<TableService> tableServiceProvider,
                       ObjectProvider<GameEventPublisher> eventPublisherProvider) {
        this.gameRepository = gameRepository;
        this.handHistoryService = handHistoryService;
        this.tableServiceProvider = tableServiceProvider;
        this.eventPublisherProvider = eventPublisherProvider;
    }

    /**
     * 新規ゲームを作成して永続化する。
     *
     * @param playerInputs  プレイヤーリスト（ID とスタック）
     * @param buttonIndex   ボタンポジションの初期インデックス
     * @param bigBlind      ビッグブラインドのチップ額
     * @param seed          乱数シード（{@code null} の場合はランダム）
     * @param oddChipRule   端数チップの配分ルール
     * @return 作成された {@link GameState}
     */
    public GameState createGame(List<PlayerInput> playerInputs, int buttonIndex, int bigBlind,
                                Long seed, OddChipRule oddChipRule) {
        List<Player> players = playerInputs.stream()
                .map(pi -> new Player(pi.id(), pi.stack()))
                .toList();
        Random rng = seed != null ? new Random(seed) : new Random();
        GameState state = GameState.newGame(players, buttonIndex, bigBlind, rng, oddChipRule);
        String id = UUID.randomUUID().toString();
        state.setId(id);
        gameRepository.create(id, state);
        return state;
    }

    /**
     * 指定 ID のゲームを取得する。
     *
     * @param id ゲーム ID
     * @return 対応する {@link GameState}
     * @throws NoSuchElementException ゲームが存在しない場合
     */
    public GameState getGame(String id) {
        return gameRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("game not found: " + id));
    }

    /**
     * 全ゲームを取得する。
     *
     * @return 全 {@link GameState} のリスト
     */
    public List<GameState> listGames() {
        return gameRepository.findAll();
    }

    /**
     * 指定テーブルで新しいハンドを開始する。
     *
     * <p>ゲームの状態が {@code finished} であることを前提とする。
     * ブラインドポスト・デッキのシャッフル・ホールカードの配布を行う。
     *
     * @param id       ゲーム ID
     * @param bigBlind このハンドのビッグブラインド額
     * @return 更新後の {@link GameState}
     */
    public GameState startHand(String id, int bigBlind) {
        GameState state = getGame(id);
        state.startHand(bigBlind);
        gameRepository.update(id, state);
        publishGame(id, state);
        publishHoleCards(id, state);
        return state;
    }

    /**
     * リングゲーム用のゲームを作成する。
     *
     * <p>通常の {@link #createGame} と異なり、初期ステータスを {@code FINISHED} に設定して
     * テーブルが「待機中」として表示されるようにする。
     * プレイヤーのスタックは 0 で初期化され、join 時に buy-in 額が設定される。
     *
     * @param playerInputs プレイヤーリスト（ID とスタック）
     * @param bigBlind     ビッグブラインドのチップ額
     * @param oddChipRule  端数チップの配分ルール
     * @return 作成された {@link GameState}
     */
    public GameState createRingGame(List<PlayerInput> playerInputs, int bigBlind, OddChipRule oddChipRule) {
        List<Player> players = playerInputs.stream()
                .map(pi -> new Player(pi.id(), pi.stack()))
                .toList();
        GameState state = GameState.newGame(players, 0, bigBlind, new Random(), oddChipRule);
        String id = UUID.randomUUID().toString();
        state.setId(id);
        state.setStatus(GameStatus.FINISHED);
        gameRepository.create(id, state);
        return state;
    }

    /**
     * ボタン位置を更新する。
     *
     * <p>SB/BB ローテーションを手動で制御する際に使用する。
     *
     * @param tableId     ゲーム ID
     * @param buttonIndex 新しいボタンポジションのシートインデックス
     */
    public void setButtonIndex(String tableId, int buttonIndex) {
        GameState state = getGame(tableId);
        state.setButtonIndex(buttonIndex);
        gameRepository.update(tableId, state);
    }

    /**
     * 指定シートのスタック額を設定する。
     *
     * <p>buy-in・cash-out・スタックリセット時に使用する。
     *
     * @param tableId   ゲーム ID
     * @param seatIndex 対象シートのインデックス
     * @param amount    新しいスタック額（chips）
     */
    public void setSeatStack(String tableId, int seatIndex, int amount) {
        GameState state = getGame(tableId);
        Player player = state.getPlayers().get(seatIndex);
        player.setStack(amount);
        gameRepository.update(tableId, state);
    }

    /**
     * 指定シートの着席状態（sitting out）を設定する。
     *
     * <p>ハンド進行中に join したプレイヤーを次のハンドまで待機させる際に使用する。
     *
     * @param tableId   ゲーム ID
     * @param seatIndex 対象シートのインデックス
     * @param value     {@code true} で sitting out、{@code false} で復帰
     */
    public void setSittingOut(String tableId, int seatIndex, boolean value) {
        GameState state = getGame(tableId);
        state.getPlayers().get(seatIndex).setSittingOut(value);
        gameRepository.update(tableId, state);
    }

    /**
     * 指定シートの現在スタック額を取得する。
     *
     * @param tableId   ゲーム ID
     * @param seatIndex 対象シートのインデックス
     * @return 現在のスタック額（chips）
     */
    public int getSeatStack(String tableId, int seatIndex) {
        GameState state = getGame(tableId);
        return state.getPlayers().get(seatIndex).getStack();
    }

    /**
     * プレイヤーアクションを適用し、ゲーム状態を更新する。
     *
     * <p>アクション適用後、ゲームが終了していれば {@link HandHistoryService#record} を呼び出す。
     * fold-win の場合は {@link TableService#processPendingLeaves} もトリガーする。
     *
     * @param id          ゲーム ID
     * @param playerIndex アクションを行うプレイヤーのシートインデックス
     * @param type        アクション種別
     * @param amount      金額（{@code bet}/{@code raise} は raise-to トータル額、{@code call} は 0）
     * @return 更新後の {@link GameState}
     */
    public GameState applyAction(String id, int playerIndex, ActionType type, int amount) {
        GameState state = getGame(id);
        Action action = Action.of(type, amount);
        state.applyAction(playerIndex, action);
        ActionRecord record = new ActionRecord(
                gameRepository.findActionsByGameId(id).size() + 1,
                playerIndex, type, amount);
        gameRepository.update(id, state, record);
        publishGame(id, state);
        recordHandHistoryIfFinished(id, state);
        if (state.isFoldWin()) {
            TableService tableService = tableServiceProvider.getIfAvailable();
            if (tableService != null) {
                tableService.processPendingLeaves(id);
            }
        }
        return state;
    }

    /**
     * 指定ゲームのアクション履歴を取得する。
     *
     * <p>ゲームが存在しない場合は例外をスローする（存在確認を兼ねる）。
     *
     * @param id ゲーム ID
     * @return アクション履歴のリスト
     * @throws NoSuchElementException ゲームが存在しない場合
     */
    public List<ActionRecord> getActions(String id) {
        getGame(id);
        return gameRepository.findActionsByGameId(id);
    }

    /**
     * ショーダウンを解決してポットを配分する。
     *
     * <p>ハンド評価・サイドポット計算・ペイアウト適用を行い、ゲームを {@code finished} 状態に遷移させる。
     * 処理後は {@link HandHistoryService#record} および {@link TableService#processPendingLeaves} を呼び出す。
     *
     * @param id ゲーム ID
     * @return ショーダウン結果（勝者・ペイアウト等）
     */
    public ShowdownResult resolveShowdown(String id) {
        GameState state = getGame(id);
        ShowdownResult result = state.resolveShowdown();
        state.applyPayouts(result.payouts());
        gameRepository.update(id, state);
        recordHandHistoryIfFinished(id, state);
        TableService tableService = tableServiceProvider.getIfAvailable();
        if (tableService != null) {
            tableService.processPendingLeaves(id);
        }
        publishGame(id, getGame(id));
        return result;
    }

    private void publishGame(String tableId, GameState state) {
        GameEventPublisher pub = eventPublisherProvider.getIfAvailable();
        if (pub != null) {
            pub.publishGameState(tableId, state);
        }
    }

    private void publishHoleCards(String tableId, GameState state) {
        GameEventPublisher pub = eventPublisherProvider.getIfAvailable();
        if (pub != null) {
            pub.publishHoleCards(tableId, state);
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
            int stackAfter = player.getStack();
            int stackBefore = stackAfter + player.getTotalContrib() - payout;
            players.add(new HandHistoryEntry.PlayerSnapshot(
                    resolvePlayerName(members, seatIndex, player.getId()),
                    seatIndex,
                    stackBefore,
                    stackAfter,
                    player.isFolded(),
                    maskHoleCards(player.getHole())
            ));
        }
        return new HandHistoryEntry(
                tableId,
                UUID.randomUUID().toString(),
                players,
                state.getLastShowdown().winnerIndexes(),
                payouts.stream().mapToInt(Integer::intValue).sum(),
                state.getStreet().getLabel(),
                Instant.now()
        );
    }

    private List<TableMemberRecord> lookupMembers(String tableId) {
        TableService tableService = tableServiceProvider.getIfAvailable();
        if (tableService == null) {
            return List.of();
        }
        try {
            return tableService.getMembers(tableId);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private String resolvePlayerName(List<TableMemberRecord> members, int seatIndex, String fallback) {
        return members.stream()
                .filter(member -> member.seatIndex() == seatIndex)
                .map(TableMemberRecord::name)
                .findFirst()
                .orElseGet(() -> (fallback != null && !fallback.isBlank())
                        ? fallback
                        : "Seat " + (seatIndex + 1));
    }

    private List<String> maskHoleCards(Card[] hole) {
        if (hole == null) {
            return List.of();
        }
        List<String> masked = new ArrayList<>();
        for (Card card : hole) {
            if (card != null) {
                masked.add(MASKED_HOLE_CARD);
            }
        }
        return List.copyOf(masked);
    }

    /**
     * ゲーム作成時のプレイヤー入力パラメータ。
     *
     * @param id    プレイヤー識別子（ユーザー名または座席識別子）
     * @param stack 初期スタック額（chips）
     */
    public record PlayerInput(String id, int stack) {}
}
