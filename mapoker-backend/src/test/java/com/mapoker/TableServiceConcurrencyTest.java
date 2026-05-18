package com.mapoker;

import com.mapoker.application.table.TableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class TableServiceConcurrencyTest {

    @Autowired
    private TableService tableService;

    @Test
    void concurrentJoinsDoNotReuseTheSameSeat() throws Exception {
        var created = tableService.createRingTable(new TableService.CreateRingTableInput(
                "Concurrent Join Table",
                9,
                10,
                "public",
                List.of("casual")
        ));

        CountDownLatch ready = new CountDownLatch(9);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(9);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < 9; i += 1) {
                final String name = "parallel_player_" + i;
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    return tableService.join(created.table().id(), name, 0).assignedSeatIndex();
                });
            }

            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(executor.submit(task));
            }

            ready.await();
            start.countDown();

            List<Integer> assignedSeats = new ArrayList<>();
            for (Future<Integer> future : futures) {
                assignedSeats.add(future.get());
            }

            assertThat(assignedSeats).hasSize(9);
            assertThat(Set.copyOf(assignedSeats)).hasSize(9);
            assertThat(tableService.getMembers(created.table().id())).hasSize(9);
            assertThat(tableService.getMembers(created.table().id()).stream()
                    .map(member -> member.seatIndex())
                    .distinct()
                    .count()).isEqualTo(9);
        } finally {
            executor.shutdownNow();
        }
    }
}
