package com.mapoker.application;

/**
 * テーブル参加者の表示用情報です。
 *
 * @param name 参加者名
 * @param seatIndex 着席位置
 * @param joinedAt 参加日時
 * @param pendingLeave 離席処理待ちかどうか
 */
public record TableMemberRecord(
        String name,
        int seatIndex,
        String joinedAt,
        boolean pendingLeave
) {
    public TableMemberRecord(String name, int seatIndex, String joinedAt) {
        this(name, seatIndex, joinedAt, false);
    }
}
