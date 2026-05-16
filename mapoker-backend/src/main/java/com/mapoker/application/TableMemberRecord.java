package com.mapoker.application;

/**
 * テーブル参加者の表示用情報です。
 *
 * @param name         参加者名（ゲーム内シート名）
 * @param seatIndex    着席位置
 * @param joinedAt     参加日時
 * @param pendingLeave 離席処理待ちかどうか
 * @param displayName  表示名（username#discriminator 形式。未認証時は name と同値）
 * @param avatarUrl    アバター画像 URL（未認証時は null）
 */
public record TableMemberRecord(
        String name,
        int seatIndex,
        String joinedAt,
        boolean pendingLeave,
        String displayName,
        String avatarUrl
) {
    /**
     * 未認証ユーザー用。{@code displayName = name}、{@code avatarUrl = null} として初期化します。
     */
    public TableMemberRecord(String name, int seatIndex, String joinedAt) {
        this(name, seatIndex, joinedAt, false, name, null);
    }

    /**
     * {@code pendingLeave} を指定するコンストラクタ。{@code displayName = name}、{@code avatarUrl = null}。
     */
    public TableMemberRecord(String name, int seatIndex, String joinedAt, boolean pendingLeave) {
        this(name, seatIndex, joinedAt, pendingLeave, name, null);
    }

    /**
     * 認証ユーザー用。{@code displayName} と {@code avatarUrl} を明示指定します。
     */
    public TableMemberRecord(String name, int seatIndex, String joinedAt, String displayName, String avatarUrl) {
        this(name, seatIndex, joinedAt, false, displayName, avatarUrl);
    }
}
