package com.mapoker.application;

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
