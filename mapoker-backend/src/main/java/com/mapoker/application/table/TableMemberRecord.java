package com.mapoker.application.table;

/**
 * テーブル参加者の表示用情報です。
 *
 * @param name         参加者名（ゲーム内シート名）
 * @param seatIndex    着席位置
 * @param joinedAt     参加日時
 * @param pendingLeave 離席処理待ちかどうか
 * @param displayName  表示名（username#discriminator 形式。未認証時は name と同値）
 * @param avatarUrl    アバター画像 URL（未認証時は null）
 * @param publicId     ユーザーの公開 ID（UUID）。未認証ゲストは null
 */
public record TableMemberRecord(
        String name,
        int seatIndex,
        String joinedAt,
        boolean pendingLeave,
        String displayName,
        String avatarUrl,
        String publicId
) {
    /** 未認証ユーザー用。 */
    public TableMemberRecord(String name, int seatIndex, String joinedAt) {
        this(name, seatIndex, joinedAt, false, name, null, null);
    }

    /** {@code pendingLeave} を指定するコンストラクタ。 */
    public TableMemberRecord(String name, int seatIndex, String joinedAt, boolean pendingLeave) {
        this(name, seatIndex, joinedAt, pendingLeave, name, null, null);
    }

    /** 認証ユーザー用。{@code displayName}・{@code avatarUrl} を指定します。 */
    public TableMemberRecord(String name, int seatIndex, String joinedAt, String displayName, String avatarUrl) {
        this(name, seatIndex, joinedAt, false, displayName, avatarUrl, null);
    }

    /** 認証ユーザー用。{@code publicId} も含めて指定します。 */
    public TableMemberRecord(String name, int seatIndex, String joinedAt,
                             String displayName, String avatarUrl, String publicId) {
        this(name, seatIndex, joinedAt, false, displayName, avatarUrl, publicId);
    }
}
