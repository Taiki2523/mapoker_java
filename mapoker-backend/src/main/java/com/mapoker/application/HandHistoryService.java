package com.mapoker.application;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HandHistoryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int TABLE_LOOKUP_LIMIT = 100;

    private final HandHistoryRepository handHistoryRepository;
    private final UserTableHistoryService userTableHistoryService;

    public HandHistoryService(HandHistoryRepository handHistoryRepository,
                              UserTableHistoryService userTableHistoryService) {
        this.handHistoryRepository = handHistoryRepository;
        this.userTableHistoryService = userTableHistoryService;
    }

    public void record(HandHistoryEntry entry) {
        if (entry == null || entry.tableId() == null || entry.tableId().isBlank()) {
            return;
        }
        handHistoryRepository.save(entry);
    }

    public List<HandHistoryEntry> listRecentForUser(String username) {
        return listRecentForUser(username, DEFAULT_LIMIT);
    }

    public List<HandHistoryEntry> listRecentForUser(String username, int limit) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return List.of();
        }
        int normalizedLimit = Math.max(1, limit);
        List<String> tableIds = userTableHistoryService
                .listRecent(normalizedUsername, Math.max(normalizedLimit, TABLE_LOOKUP_LIMIT)).stream()
                .map(UserTableHistoryEntry::tableId)
                .distinct()
                .toList();
        if (tableIds.isEmpty()) {
            return List.of();
        }
        return handHistoryRepository.findRecentByTableIds(tableIds, normalizedLimit);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return username.trim();
    }
}
