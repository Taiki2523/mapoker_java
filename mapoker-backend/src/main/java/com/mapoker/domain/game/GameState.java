package com.mapoker.domain.game;

import com.mapoker.domain.PokerConstants;
import com.mapoker.domain.card.Card;
import com.mapoker.domain.card.Deck;
import com.mapoker.domain.card.Rank;
import com.mapoker.domain.hand.HandEvaluator;
import com.mapoker.domain.hand.HandRank;
import com.mapoker.domain.hand.HandValue;
import com.mapoker.domain.rules.Action;
import com.mapoker.domain.rules.ActionValidator;
import com.mapoker.domain.rules.PlayerState;
import com.mapoker.domain.rules.Street;
import com.mapoker.domain.rules.TableState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Texas Hold'em 1ゲームの進行状態をすべて保持するドメインオブジェクト。
 *
 * <p>このクラスはゲームのコアロジック（ブラインド投下・アクション適用・ストリート進行・
 * サイドポット分配・ショーダウン解決）を担う。Spring 非依存の純粋 Java クラス。
 *
 * <h2>ライフサイクル</h2>
 * <ol>
 *   <li>{@link #newGame} でゲームインスタンスを生成する</li>
 *   <li>{@link #startHand} で各ハンドを開始（ブラインド投下・カード配布）する</li>
 *   <li>{@link #applyAction} でプレイヤーのアクションを適用する（バリデーション含む）</li>
 *   <li>ストリートが進み {@code status == SHOWDOWN} になったら {@link #resolveShowdown} を呼ぶ</li>
 *   <li>{@link #applyPayouts} でポットを配分し、ステータスが {@code FINISHED} になる</li>
 * </ol>
 *
 * <h2>制約</h2>
 * <ul>
 *   <li>{@code bet}/{@code raise} の {@code amount} は増分ではなく合計額（raise-to total）</li>
 *   <li>最小レイズ = {@code max(bigBlind, lastRaiseSize)}。サブミニオールインは許容するが
 *       {@code raiseOpen=false} にしてベッティングを再オープンしない</li>
 *   <li>奇数チップの端数配分は {@link OddChipRule} で制御する</li>
 * </ul>
 */
public class GameState {

    private String id;
    private List<Player> players;
    private List<Card> deck;
    private int deckPos;
    private List<Card> community;
    private int pot;
    private int buttonIndex;
    private int smallBlindIdx;
    private int bigBlindIdx;
    private int currentPlayer;
    private int currentBet;
    private int lastRaiseSize;
    private int bigBlindSize;
    private Street street;
    private GameStatus status;
    private boolean[] acted;
    private OddChipRule oddChipRule;
    private boolean raiseOpen;
    private ShowdownResult lastShowdown;
    private boolean foldWin = false;

    private GameState() {}

    /**
     * 永続化からの復元専用の空インスタンスを生成する。
     * リポジトリ実装がデシリアライズ後に各フィールドをセッターで埋める用途に限定する。
     *
     * @return フィールド未設定の空 {@link GameState}
     */
    public static GameState empty() {
        return new GameState();
    }

    /**
     * 新しいゲームインスタンスを生成する。プレイヤーリストはディープコピーされる。
     *
     * <p>このメソッドはデッキの生成とシャッフルのみを行い、ブラインド投下やカード配布は
     * 行わない。ハンドを開始するには別途 {@link #startHand} を呼ぶこと。
     *
     * @param players     参加プレイヤーのリスト（2〜9人）
     * @param buttonIndex ボタン位置（プレイヤーリストの 0-based インデックス）
     * @param bigBlind    ビッグブラインド額（正の値）
     * @param rng         シャッフル用乱数生成器。{@code null} の場合はデフォルト {@link Random}
     * @param oddChipRule 奇数チップの配分ルール。{@code null} の場合は {@link OddChipRule#LOW_INDEX}
     * @return 初期化済みの {@link GameState}
     * @throws IllegalArgumentException プレイヤー数・ビッグブラインド額・ボタン位置が不正な場合
     */
    public static GameState newGame(List<Player> players, int buttonIndex, int bigBlind, Random rng, OddChipRule oddChipRule) {
        if (players.size() < PokerConstants.MIN_PLAYERS || players.size() > PokerConstants.MAX_PLAYERS)
            throw new IllegalArgumentException("players must be 2-9");
        if (bigBlind <= 0)
            throw new IllegalArgumentException("big blind must be positive");
        if (buttonIndex < 0 || buttonIndex >= players.size())
            throw new IllegalArgumentException("invalid button index");

        GameState g = new GameState();
        g.players = new ArrayList<>();
        for (Player p : players) g.players.add(new Player(p));
        g.buttonIndex = buttonIndex;
        g.street = Street.PREFLOP;
        g.status = GameStatus.IN_PROGRESS;
        g.currentBet = 0;
        g.lastRaiseSize = bigBlind;
        g.bigBlindSize = bigBlind;
        g.oddChipRule = oddChipRule != null ? oddChipRule : OddChipRule.LOW_INDEX;
        g.raiseOpen = true;
        g.deck = Deck.newDeck();
        Deck.shuffle(g.deck, rng);
        g.acted = new boolean[players.size()];
        g.community = new ArrayList<>();
        return g;
    }

    /**
     * 新しいハンドを開始する。ボタンを次のアクティブプレイヤーに進め、ブラインドを投下し、
     * 各プレイヤーにホールカードを2枚配布する。
     *
     * <p>チップが0のプレイヤーは自動的にフォールド扱いになる。
     * ヘッズアップ（2人）のときはボタン = スモールブラインドとなる（ポーカールール準拠）。
     *
     * @param bigBlind ビッグブラインド額
     * @throws IllegalStateException ショーダウンが未解決の場合、またはチップ保有者が2人未満の場合
     */
    public void startHand(int bigBlind) {
        if (status == GameStatus.SHOWDOWN)
            throw new IllegalStateException("cannot start hand: showdown not resolved");
        status = GameStatus.IN_PROGRESS;
        foldWin = false;
        lastShowdown = null;
        community.clear();
        deckPos = 0;
        pot = 0;
        currentBet = 0;
        lastRaiseSize = bigBlind;
        bigBlindSize = bigBlind;
        raiseOpen = true;
        street = Street.PREFLOP;

        for (Player p : players) {
            p.setFolded(false);
            p.setAllIn(false);
            p.setSittingOut(false);
            p.setContributed(0);
            p.setTotalContrib(0);
            p.setHole(new Card[PokerConstants.HOLE_CARDS]);
        }

        int activePlayers = 0;
        for (Player p : players) {
            if (p.getStack() <= 0) {
                p.setFolded(true);
            } else {
                activePlayers++;
            }
        }
        if (activePlayers < PokerConstants.MIN_PLAYERS)
            throw new IllegalStateException("not enough players with chips");

        buttonIndex = nextActive(buttonIndex);

        // Shuffle fresh deck for new hand
        deck = Deck.newDeck();
        Deck.shuffle(deck, null);

        if (activePlayers == 2) {
            smallBlindIdx = buttonIndex;
            bigBlindIdx = nextActive(buttonIndex);
        } else {
            smallBlindIdx = nextActive(buttonIndex);
            bigBlindIdx = nextActive(smallBlindIdx);
        }

        postBlind(smallBlindIdx, bigBlind / PokerConstants.SMALL_BLIND_DIVISOR);
        postBlind(bigBlindIdx, bigBlind);

        for (Player p : players) {
            if (!p.isFolded() && p.getStack() <= 0) {
                p.setAllIn(true);
            }
        }

        for (int i = 0; i < 2; i++) {
            for (Player p : players) {
                if (p.isFolded()) continue;
                Card[] hole = p.getHole();
                hole[i] = draw();
            }
        }

        currentBet = players.get(bigBlindIdx).getContributed();
        lastRaiseSize = bigBlind;
        bigBlindSize = bigBlind;
        resetActed();
        raiseOpen = true;
        acted = new boolean[players.size()];
        currentPlayer = firstToAct();
    }

    public void applyAction(int playerIndex, Action action) {
        if (status != GameStatus.IN_PROGRESS)
            throw new IllegalStateException("game not in progress");
        if (playerIndex != currentPlayer)
            throw new IllegalStateException("not current player");

        Player p = players.get(playerIndex);
        TableState table = new TableState(street, bigBlindSize, currentBet, lastRaiseSize, raiseOpen);
        PlayerState playerState = new PlayerState(p.getStack(), p.getContributed(), p.isFolded(), p.isAllIn());
        ActionValidator.validate(table, playerState, action);

        switch (action.type()) {
            case FOLD -> {
                players.get(playerIndex).setFolded(true);
                acted[playerIndex] = true;
            }
            case CHECK -> acted[playerIndex] = true;
            case CALL -> {
                int target = action.amount();
                if (target == 0) {
                    target = Math.min(currentBet, p.getContributed() + p.getStack());
                }
                commitBet(playerIndex, target);
                acted[playerIndex] = true;
            }
            case BET -> {
                commitBet(playerIndex, action.amount());
                currentBet = action.amount();
                lastRaiseSize = action.amount();
                resetActedExcept(playerIndex);
                raiseOpen = true;
            }
            case RAISE -> {
                int raiseSize = action.amount() - currentBet;
                commitBet(playerIndex, action.amount());
                currentBet = action.amount();
                lastRaiseSize = raiseSize;
                resetActedExcept(playerIndex);
                raiseOpen = true;
            }
            case ALL_IN -> {
                int target = p.getContributed() + p.getStack();
                commitBet(playerIndex, target);
                if (target > currentBet) {
                    int raiseSize = target - currentBet;
                    int previousBet = currentBet;
                    currentBet = target;
                    if (raiseSize >= minRaiseSize(bigBlindSize, previousBet)) {
                        lastRaiseSize = raiseSize;
                        raiseOpen = true;
                        resetActedExcept(playerIndex);
                    } else {
                        raiseOpen = false;
                        acted[playerIndex] = true;
                    }
                } else {
                    acted[playerIndex] = true;
                }
            }
        }

        // If only 1 non-folded player remains, auto-resolve
        if (remainingActive() <= 1) {
            List<Integer> winners = remainingIndexes();
            List<Integer> payouts = splitPots(winners);
            ShowdownResult result = new ShowdownResult(winners, new HandValue(HandRank.HIGH_CARD, List.of()), payouts);
            lastShowdown = result;
            foldWin = true;
            applyPayouts(payouts);
            return;
        }

        if (isBettingRoundComplete()) {
            if (activeCount() <= 1) {
                // all players are all-in, run out the board
                while (street != Street.RIVER) {
                    advanceStreet();
                }
                status = GameStatus.SHOWDOWN;
                return;
            }
            if (street == Street.RIVER) {
                status = GameStatus.SHOWDOWN;
                return;
            }
            advanceStreet();
            return;
        }

        currentPlayer = nextToAct(currentPlayer);
    }

    public ShowdownResult resolveShowdown() {
        if (status != GameStatus.SHOWDOWN)
            throw new IllegalStateException("showdown not available in status " + status);
        if (community.size() < PokerConstants.COMMUNITY_CARDS)
            throw new IllegalStateException("community cards incomplete");

        HandValue best = new HandValue(HandRank.HIGH_CARD, List.of(Rank.TWO));
        List<Integer> winners = new ArrayList<>();

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p.isFolded()) continue;
            if (p.isSittingOut()) continue;
            Card[] seven = buildSeven(p.getHole(), community);
            HandValue val = HandEvaluator.eval7(seven);
            int cmp = val.compareTo(best);
            if (cmp > 0) {
                best = val;
                winners.clear();
                winners.add(i);
            } else if (cmp == 0) {
                winners.add(i);
            }
        }
        if (winners.isEmpty())
            throw new IllegalStateException("no active players at showdown");

        List<Integer> payouts = splitPots(winners);
        ShowdownResult result = new ShowdownResult(List.copyOf(winners), best, payouts);
        lastShowdown = result;
        return result;
    }

    public void applyPayouts(List<Integer> payouts) {
        if (payouts.size() != players.size())
            throw new IllegalArgumentException("payouts length mismatch");
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setStack(players.get(i).getStack() + payouts.get(i));
        }
        pot = 0;
        currentBet = 0;
        lastRaiseSize = bigBlindSize;
        status = GameStatus.FINISHED;
    }

    public boolean canStartHand() {
        int eligible = 0;
        for (Player p : players) {
            if (p.getStack() > 0 && !p.isSittingOut()) eligible++;
        }
        return eligible >= 2;
    }

    // ---- private helpers ----

    private void advanceStreet() {
        street = street.next();
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setContributed(0);
            acted[i] = false;
        }
        currentBet = 0;
        lastRaiseSize = bigBlindSize;
        raiseOpen = true;
        burn();
        switch (street) {
            case FLOP -> { community.add(draw()); community.add(draw()); community.add(draw()); }
            case TURN, RIVER -> community.add(draw());
            default -> {}
        }
        currentPlayer = firstToAct();
    }

    private void postBlind(int playerIndex, int amount) {
        if (amount <= 0) return;
        Player p = players.get(playerIndex);
        if (p.getStack() < amount) amount = p.getStack();
        if (amount == 0) return;
        p.setStack(p.getStack() - amount);
        p.setContributed(p.getContributed() + amount);
        p.setTotalContrib(p.getTotalContrib() + amount);
        if (p.getStack() == 0) p.setAllIn(true);
        pot += amount;
    }

    private void commitBet(int playerIndex, int target) {
        Player p = players.get(playerIndex);
        int current = p.getContributed();
        if (target < current) return;
        int delta = target - current;
        if (delta > p.getStack()) {
            delta = p.getStack();
            target = current + delta;
        }
        p.setStack(p.getStack() - delta);
        p.setContributed(target);
        p.setTotalContrib(p.getTotalContrib() + delta);
        if (p.getStack() == 0) p.setAllIn(true);
        pot += delta;
    }

    private Card draw() {
        return deck.get(deckPos++);
    }

    private void burn() {
        draw();
    }

    private void resetActed() {
        for (int i = 0; i < acted.length; i++) acted[i] = false;
    }

    private void resetActedExcept(int playerIndex) {
        for (int i = 0; i < acted.length; i++) {
            if (i == playerIndex) {
                acted[i] = true;
            } else if (isActive(i)) {
                acted[i] = false;
            } else {
                acted[i] = true;
            }
        }
    }

    private boolean isActive(int i) {
        return !players.get(i).isFolded() && !players.get(i).isAllIn();
    }

    private int remainingActive() {
        int count = 0;
        for (Player p : players) {
            if (!p.isFolded() && !p.isSittingOut()) count++;
        }
        return count;
    }

    private List<Integer> remainingIndexes() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (!players.get(i).isFolded()) out.add(i);
        }
        return out;
    }

    private int activeCount() {
        int count = 0;
        for (int i = 0; i < players.size(); i++) if (isActive(i)) count++;
        return count;
    }

    private boolean isBettingRoundComplete() {
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p.isFolded()) continue;
            if (p.isAllIn()) continue;
            if (p.isSittingOut()) continue;
            if (p.getContributed() != currentBet) return false;
            if (!acted[i]) return false;
        }
        return true;
    }

    private int firstToAct() {
        return street == Street.PREFLOP ? nextActive(bigBlindIdx) : nextActive(buttonIndex);
    }

    private int nextToAct(int from) {
        return nextActive(from);
    }

    private int nextActive(int from) {
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int idx = (from + i) % n;
            Player p = players.get(idx);
            if (p.isFolded()) continue;
            if (p.isAllIn()) continue;
            if (p.isSittingOut()) continue;
            return idx;
        }
        return from;
    }

    // ---- showdown / side-pot helpers ----

    private static Card[] buildSeven(Card[] hole, List<Card> community) {
        Card[] out = new Card[PokerConstants.TOTAL_EVAL_CARDS];
        out[0] = hole[0];
        out[1] = hole[1];
        for (int i = 0; i < PokerConstants.COMMUNITY_CARDS; i++) out[PokerConstants.HOLE_CARDS + i] = community.get(i);
        return out;
    }

    private List<Integer> splitPots(List<Integer> winners) {
        int[] payouts = new int[players.size()];
        List<SidePot> pots = buildSidePots();
        for (SidePot sp : pots) {
            List<Integer> eligible = intersect(sp.eligible(), winners);
            if (eligible.isEmpty()) continue;
            int base = sp.amount() / eligible.size();
            int rem = sp.amount() % eligible.size();
            for (int idx : eligible) payouts[idx] += base;
            distributeRemainder(payouts, eligible, rem);
        }
        List<Integer> result = new ArrayList<>(players.size());
        for (int v : payouts) result.add(v);
        return result;
    }

    private record SidePot(int amount, List<Integer> eligible) {}

    private List<SidePot> buildSidePots() {
        record Entry(int index, int contrib) {}
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            int tc = players.get(i).getTotalContrib();
            if (tc > 0) entries.add(new Entry(i, tc));
        }
        if (entries.isEmpty()) return List.of();
        entries.sort(java.util.Comparator.comparingInt(Entry::contrib));

        List<SidePot> pots = new ArrayList<>();
        List<Entry> remaining = new ArrayList<>(entries);
        int prev = 0;
        while (!remaining.isEmpty()) {
            int level = remaining.get(0).contrib();
            int amount = (level - prev) * remaining.size();
            List<Integer> eligible = new ArrayList<>();
            for (Entry e : remaining) {
                if (!players.get(e.index()).isFolded()) eligible.add(e.index());
            }
            pots.add(new SidePot(amount, eligible));
            prev = level;
            List<Entry> next = new ArrayList<>();
            for (Entry e : remaining) {
                if (e.contrib() > level) next.add(e);
            }
            remaining = next;
        }
        return pots;
    }

    private static List<Integer> intersect(List<Integer> eligible, List<Integer> winners) {
        Set<Integer> set = new HashSet<>(winners);
        List<Integer> out = new ArrayList<>();
        for (int e : eligible) if (set.contains(e)) out.add(e);
        return out;
    }

    private void distributeRemainder(int[] payouts, List<Integer> winners, int rem) {
        if (rem <= 0) return;
        if (oddChipRule == OddChipRule.BUTTON_LEFT) {
            List<Integer> order = orderByButtonLeft(winners);
            for (int i = 0; i < rem; i++) payouts[order.get(i % order.size())]++;
        } else {
            for (int i = 0; i < rem; i++) payouts[winners.get(i % winners.size())]++;
        }
    }

    private List<Integer> orderByButtonLeft(List<Integer> winners) {
        Set<Integer> set = new HashSet<>(winners);
        List<Integer> out = new ArrayList<>();
        int n = players.size();
        for (int i = 1; i <= n; i++) {
            int idx = (buttonIndex + i) % n;
            if (set.contains(idx)) out.add(idx);
        }
        return out;
    }

    private static int minRaiseSize(int bigBlind, int lastRaise) {
        return Math.max(bigBlind, lastRaise);
    }

    // ---- getters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<Player> getPlayers() { return players; }
    public List<Card> getDeck() { return deck; }
    public void setDeck(List<Card> deck) { this.deck = deck; }
    public int getDeckPos() { return deckPos; }
    public void setDeckPos(int deckPos) { this.deckPos = deckPos; }
    public List<Card> getCommunity() { return community; }
    public int getPot() { return pot; }
    public void setPot(int pot) { this.pot = pot; }
    public int getButtonIndex() { return buttonIndex; }
    public int getSmallBlindIdx() { return smallBlindIdx; }
    public int getBigBlindIdx() { return bigBlindIdx; }
    public int getCurrentPlayer() { return currentPlayer; }
    public int getCurrentBet() { return currentBet; }
    public int getLastRaiseSize() { return lastRaiseSize; }
    public int getBigBlindSize() { return bigBlindSize; }
    public Street getStreet() { return street; }
    public GameStatus getStatus() { return status; }
    public boolean[] getActed() { return acted; }
    public OddChipRule getOddChipRule() { return oddChipRule; }
    public boolean isRaiseOpen() { return raiseOpen; }
    public boolean isFoldWin() { return foldWin; }
    public ShowdownResult getLastShowdown() { return lastShowdown; }
    public void setStatus(GameStatus status) { this.status = status; }
    public void setPlayers(List<Player> players) { this.players = players; }
    public void setCommunity(List<Card> community) { this.community = community; }
    public void setButtonIndex(int buttonIndex) { this.buttonIndex = buttonIndex; }
    public void setSmallBlindIdx(int smallBlindIdx) { this.smallBlindIdx = smallBlindIdx; }
    public void setBigBlindIdx(int bigBlindIdx) { this.bigBlindIdx = bigBlindIdx; }
    public void setCurrentPlayer(int currentPlayer) { this.currentPlayer = currentPlayer; }
    public void setCurrentBet(int currentBet) { this.currentBet = currentBet; }
    public void setLastRaiseSize(int lastRaiseSize) { this.lastRaiseSize = lastRaiseSize; }
    public void setBigBlindSize(int bigBlindSize) { this.bigBlindSize = bigBlindSize; }
    public void setStreet(Street street) { this.street = street; }
    public void setActed(boolean[] acted) { this.acted = acted; }
    public void setOddChipRule(OddChipRule oddChipRule) { this.oddChipRule = oddChipRule; }
    public void setRaiseOpen(boolean raiseOpen) { this.raiseOpen = raiseOpen; }
    public void setFoldWin(boolean foldWin) { this.foldWin = foldWin; }
    public void setLastShowdown(ShowdownResult lastShowdown) { this.lastShowdown = lastShowdown; }
}
