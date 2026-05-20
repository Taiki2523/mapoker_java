package com.mapoker.domain;

/**
 * Texas Hold'em のルールとして固定された定数。
 *
 * <p>ポーカールール固有の値はここに一元管理する。環境依存の設定（デフォルトのビッグブラインド額など）は
 * {@code application.properties} で管理し、このクラスには含めない。
 * ルール変更がない限りこのクラスの値は変更しないこと。
 */
public final class PokerConstants {

    private PokerConstants() {}

    /** テーブルに参加できる最小プレイヤー数。2人未満ではハンドを開始できない。 */
    public static final int MIN_PLAYERS = 2;

    /** テーブルに参加できる最大プレイヤー数。 */
    public static final int MAX_PLAYERS = 9;

    /** 標準デッキのカード枚数（4スーツ × 13ランク）。 */
    public static final int DECK_SIZE = 52;

    /** スーツの種類数。フラッシュ判定用の配列サイズに使用する。 */
    public static final int NUM_SUITS = 4;

    /**
     * ランク値を配列インデックスとして使う際の配列サイズ。
     * Ace = 14 なので 15 を確保し、インデックス 0・1 は未使用となる。
     */
    public static final int RANK_ARRAY_SIZE = 15;

    /** 各プレイヤーに配るホールカードの枚数。 */
    public static final int HOLE_CARDS = 2;

    /** ボードに公開されるコミュニティカードの合計枚数（フロップ3 + ターン1 + リバー1）。 */
    public static final int COMMUNITY_CARDS = 5;

    /** ハンド評価に使う 7 枚（ホール 2 + コミュニティ 5）。{@link com.mapoker.domain.hand.HandEvaluator#eval7} が全組み合わせを評価する。 */
    public static final int TOTAL_EVAL_CARDS = 7;

    /** 1 ハンドを構成するカード枚数（5 枚評価）。{@link com.mapoker.domain.hand.HandEvaluator#eval5} の入力サイズ。 */
    public static final int HAND_SIZE = 5;

    /** フロップで一度に公開するカード枚数。 */
    public static final int FLOP_CARDS = 3;

    /** フラッシュ成立に必要な同スーツのカード枚数。 */
    public static final int FLUSH_SIZE = 5;

    /** ストレート成立に必要な連続するランクの枚数。 */
    public static final int STRAIGHT_LENGTH = 5;

    /**
     * スモールブラインド額を算出する除数。
     * スモールブラインド = ビッグブラインド / {@code SMALL_BLIND_DIVISOR}。
     */
    public static final int SMALL_BLIND_DIVISOR = 2;
}
