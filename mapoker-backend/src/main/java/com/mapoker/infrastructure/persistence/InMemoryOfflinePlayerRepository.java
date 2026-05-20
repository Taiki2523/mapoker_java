package com.mapoker.infrastructure.persistence;

import com.mapoker.application.ports.OfflinePlayerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("local")
public class InMemoryOfflinePlayerRepository implements OfflinePlayerRepository {

    private record MutableData(int buyIn, Integer cashOut, Instant cashoutAt) {}

    private final Map<String, Map<Integer, MutableData>> store = new ConcurrentHashMap<>();

    @Override
    public void addBuyIn(String gameId, int seatIndex, int amount) {
        store.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                .merge(seatIndex,
                        new MutableData(amount, null, null),
                        (old, n) -> new MutableData(old.buyIn() + amount, old.cashOut(), old.cashoutAt()));
    }

    @Override
    public void setCashout(String gameId, int seatIndex, int cashoutAmount, Instant cashoutAt) {
        store.computeIfAbsent(gameId, k -> new ConcurrentHashMap<>())
                .merge(seatIndex,
                        new MutableData(0, cashoutAmount, cashoutAt),
                        (old, n) -> new MutableData(old.buyIn(), cashoutAmount, cashoutAt));
    }

    @Override
    public List<OfflinePlayerData> findByGameId(String gameId) {
        Map<Integer, MutableData> seats = store.getOrDefault(gameId, Map.of());
        List<OfflinePlayerData> result = new ArrayList<>();
        seats.forEach((seat, d) ->
                result.add(new OfflinePlayerData(seat, d.buyIn(), d.cashOut(), d.cashoutAt())));
        result.sort((a, b) -> Integer.compare(a.seatIndex(), b.seatIndex()));
        return result;
    }
}
