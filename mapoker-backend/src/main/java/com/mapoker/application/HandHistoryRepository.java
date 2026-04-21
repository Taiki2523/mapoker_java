package com.mapoker.application;

import java.util.List;

public interface HandHistoryRepository {

    void save(HandHistoryEntry entry);

    List<HandHistoryEntry> findRecentByTableIds(List<String> tableIds, int limit);
}
