package com.mapoker.domain;

/** Texas Hold'em のルールとして固定された定数。設定ファイルに出してはいけない。 */
public final class PokerConstants {

    private PokerConstants() {}

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 9;

    public static final int DECK_SIZE = 52;
    public static final int NUM_SUITS = 4;
    /** Rank 値の配列インデックス上限（Ace = 14、0-1 は未使用）*/
    public static final int RANK_ARRAY_SIZE = 15;

    public static final int HOLE_CARDS = 2;
    public static final int COMMUNITY_CARDS = 5;
    /** ハンド評価に使う 7 枚（ホール 2 + コミュニティ 5）*/
    public static final int TOTAL_EVAL_CARDS = 7;
    /** 1 ハンドを構成するカード枚数（5 枚評価）*/
    public static final int HAND_SIZE = 5;
    /** フロップで公開するカード枚数 */
    public static final int FLOP_CARDS = 3;

    /** フラッシュ判定に必要な同スーツ枚数 */
    public static final int FLUSH_SIZE = 5;
    /** ストレートを構成する連続ランク枚数 */
    public static final int STRAIGHT_LENGTH = 5;

    /** スモールブラインドはビッグブラインドを 2 で割った値 */
    public static final int SMALL_BLIND_DIVISOR = 2;
}
