package com.mapoker.application;

import java.util.List;

public interface UserTableHistoryRepository {

    void recordJoin(String username, TableRecord table, int seatIndex);

    void recordLeave(String username, String tableId, Integer seatIndex);

    List<UserTableHistoryEntry> findRecentByUsername(String username, int limit);
}
