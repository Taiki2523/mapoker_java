package com.mapoker.application;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserTableHistoryService {

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private final UserTableHistoryRepository historyRepository;

    public UserTableHistoryService(UserTableHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    public void recordJoin(String username, TableRecord table, int seatIndex) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return;
        }
        historyRepository.recordJoin(normalizedUsername, table, seatIndex);
    }

    public void recordLeave(String username, String tableId, Integer seatIndex) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null || tableId == null || tableId.isBlank()) {
            return;
        }
        historyRepository.recordLeave(normalizedUsername, tableId, seatIndex);
    }

    public List<UserTableHistoryEntry> listRecent(String username) {
        return listRecent(username, DEFAULT_HISTORY_LIMIT);
    }

    public List<UserTableHistoryEntry> listRecent(String username, int limit) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return List.of();
        }
        return historyRepository.findRecentByUsername(normalizedUsername, Math.max(1, limit));
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim();
    }
}
