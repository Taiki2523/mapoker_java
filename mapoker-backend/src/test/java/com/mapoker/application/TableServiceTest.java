package com.mapoker.application;

import com.mapoker.domain.game.OddChipRule;
import com.mapoker.infrastructure.config.GameProperties;
import com.mapoker.infrastructure.config.WalletProperties;
import com.mapoker.infrastructure.messaging.GameEventPublisher;
import com.mapoker.infrastructure.persistence.InMemoryGameRepository;
import com.mapoker.infrastructure.persistence.InMemoryHandHistoryRepository;
import com.mapoker.infrastructure.persistence.InMemoryUserTableHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * TableService の単体テスト。
 *
 * <p>Spring を起動せず、InMemory 実装を直接注入する。
 * WalletService と GameEventPublisher は null を返す ObjectProvider で stub する
 * （コードが getIfAvailable()==null を許容しているため）。
 *
 * <p>table-join-bb 追加時に join() の変更が既存ケースを壊さないことを確認する基準テストとして機能する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TableServiceTest {

    @Mock ObjectProvider<WalletService> walletProvider;
    @Mock ObjectProvider<GameEventPublisher> publisherProvider;
    @Mock ObjectProvider<TableService> tableServiceProvider;

    private TableService tableService;

    private static final GameProperties GAME_PROPS =
            new GameProperties(OddChipRule.LOW_INDEX, "Player");
    private static final WalletProperties WALLET_PROPS =
            new WalletProperties(10000, 1000, 24, 1000, 5000, 12, 20, 100, List.of());

    /** デフォルトテーブル作成入力（2人・BB=10・public） */
    private static TableService.CreateRingTableInput defaultInput() {
        return new TableService.CreateRingTableInput("Test Table", 2, 10, "public", List.of("casual"));
    }

    @BeforeEach
    void setUp() {
        // ObjectProvider は常に null を返す（Wallet・Publisher 不要なテスト用）
        when(walletProvider.getIfAvailable()).thenReturn(null);
        when(publisherProvider.getIfAvailable()).thenReturn(null);
        when(tableServiceProvider.getObject()).thenReturn(null);

        var gameRepo = new InMemoryGameRepository();
        var historyRepo = new InMemoryHandHistoryRepository();
        var userTableHistoryRepo = new InMemoryUserTableHistoryRepository();
        var userTableHistoryService = new UserTableHistoryService(userTableHistoryRepo);
        var handHistoryService = new HandHistoryService(historyRepo, userTableHistoryService);
        var gameService = new GameService(gameRepo, handHistoryService, tableServiceProvider, publisherProvider);

        tableService = new TableService(
                gameService, GAME_PROPS, WALLET_PROPS,
                userTableHistoryService, walletProvider, publisherProvider);
    }

    // -----------------------------------------------------------------------
    // createRingTable — 正常系
    // -----------------------------------------------------------------------

    @Test
    void createTableReturnsTableWithCorrectProperties() {
        var result = tableService.createRingTable(defaultInput());
        assertThat(result.table().name()).isEqualTo("Test Table");
        assertThat(result.table().bigBlind()).isEqualTo(10);
        assertThat(result.table().maxPlayers()).isEqualTo(2);
        assertThat(result.table().visibility()).isEqualTo("public");
        assertThat(result.table().flags()).containsExactly("casual");
        assertThat(result.table().id()).isNotBlank();
    }

    @Test
    void createTableAutoGeneratesNameWhenBlank() {
        var input = new TableService.CreateRingTableInput(null, 2, 10, "public", null);
        var result = tableService.createRingTable(input);
        assertThat(result.table().name()).startsWith("Cash Orbit");
    }

    @Test
    void createTableNormalizesPrivateVisibility() {
        var input = new TableService.CreateRingTableInput("T", 2, 10, "private", null);
        assertThat(tableService.createRingTable(input).table().visibility()).isEqualTo("private");
    }

    @Test
    void createTableDefaultsToPublicForUnknownVisibility() {
        var input = new TableService.CreateRingTableInput("T", 2, 10, null, null);
        assertThat(tableService.createRingTable(input).table().visibility()).isEqualTo("public");
    }

    @Test
    void createTableDefaultsFlagsToCasualWhenNull() {
        var input = new TableService.CreateRingTableInput("T", 2, 10, "public", null);
        assertThat(tableService.createRingTable(input).table().flags()).containsExactly("casual");
    }

    @Test
    void createTableNormalizesFlags() {
        var input = new TableService.CreateRingTableInput("T", 2, 10, "public", List.of("Newbie", "CASUAL", "casual"));
        var flags = tableService.createRingTable(input).table().flags();
        // 大文字→小文字、重複除去
        assertThat(flags).containsExactlyInAnyOrder("newbie", "casual");
    }

    @Test
    void createTableMinBuyInIsBigBlindTimesMultiplier() {
        var result = tableService.createRingTable(defaultInput());
        // minBuyinBbMultiplier=20, BB=10 → 200
        assertThat(result.table().minBuyIn()).isEqualTo(200);
    }

    // -----------------------------------------------------------------------
    // createRingTable — バリデーション
    // -----------------------------------------------------------------------

    @Test
    void createTableThrowsForPlayerCountTooSmall() {
        var input = new TableService.CreateRingTableInput("T", 1, 10, "public", null);
        assertThatThrownBy(() -> tableService.createRingTable(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("player count");
    }

    @Test
    void createTableThrowsForPlayerCountTooLarge() {
        var input = new TableService.CreateRingTableInput("T", 10, 10, "public", null);
        assertThatThrownBy(() -> tableService.createRingTable(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("player count");
    }

    @Test
    void createTableThrowsForZeroBigBlind() {
        var input = new TableService.CreateRingTableInput("T", 2, 0, "public", null);
        assertThatThrownBy(() -> tableService.createRingTable(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("big blind");
    }

    // -----------------------------------------------------------------------
    // join — 正常系
    // -----------------------------------------------------------------------

    @Test
    void joinAssignsSeatToNewMember() {
        var table = tableService.createRingTable(defaultInput()).table();
        var result = tableService.join(table.id(), "alice", 0);
        assertThat(result.assignedSeatIndex()).isBetween(0, 1);
        assertThat(result.members()).hasSize(1);
        assertThat(result.members().get(0).name()).isEqualTo("alice");
    }

    @Test
    void joinTwoMembersFillsSeats() {
        var table = tableService.createRingTable(defaultInput()).table();
        tableService.join(table.id(), "alice", 0);
        var result = tableService.join(table.id(), "bob", 0);
        assertThat(result.members()).hasSize(2);
    }

    @Test
    void joinWithSameNameReturnsExistingSeat() {
        var table = tableService.createRingTable(defaultInput()).table();
        var first = tableService.join(table.id(), "alice", 0);
        var second = tableService.join(table.id(), "alice", 0);
        assertThat(second.assignedSeatIndex()).isEqualTo(first.assignedSeatIndex());
        assertThat(second.members()).hasSize(1);
    }

    @Test
    void joinWithBlankNameUsesDefaultPlayerName() {
        var table = tableService.createRingTable(defaultInput()).table();
        var result = tableService.join(table.id(), "  ", 0);
        assertThat(result.members().get(0).name()).isEqualTo("Player");
    }

    @Test
    void joinWithNullNameUsesDefaultPlayerName() {
        var table = tableService.createRingTable(defaultInput()).table();
        var result = tableService.join(table.id(), null, 0);
        assertThat(result.members().get(0).name()).isEqualTo("Player");
    }

    // -----------------------------------------------------------------------
    // join — テーブル満席
    // -----------------------------------------------------------------------

    @Test
    void joinThrowsWhenTableFull() {
        var table = tableService.createRingTable(defaultInput()).table(); // maxPlayers=2
        tableService.join(table.id(), "alice", 0);
        tableService.join(table.id(), "bob", 0);
        assertThatThrownBy(() -> tableService.join(table.id(), "charlie", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("full");
    }

    // -----------------------------------------------------------------------
    // join — ハンド進行中は sittingOut=true で着席
    // -----------------------------------------------------------------------

    @Test
    void joinDuringActiveHandSetsSittingOut() {
        // 2人テーブルを作って先に2人入れてハンド開始、3人目を途中参加させる試みは
        // maxPlayers=2 で満席になるため、3人テーブルで確認する
        var input = new TableService.CreateRingTableInput("T", 3, 10, "public", null);
        var table = tableService.createRingTable(input).table();
        tableService.join(table.id(), "alice", 1000);
        tableService.join(table.id(), "bob", 1000);
        // ハンド開始（pot>0 → active）
        tableService.startHand(table.id(), 10);

        // ハンド中に charlie が参加 → sittingOut=true でゲームに反映されているはず
        var result = tableService.join(table.id(), "charlie", 0);
        assertThat(result.members()).hasSize(3);
        // sittingOut の確認はゲーム状態を直接見る
        var game = tableService.getTableGame(table.id());
        int charlieSeat = result.assignedSeatIndex();
        assertThat(game.getPlayers().get(charlieSeat).isSittingOut()).isTrue();
    }

    // -----------------------------------------------------------------------
    // leave — 正常系
    // -----------------------------------------------------------------------

    @Test
    void leaveRemovesMemberWhenHandNotActive() {
        var table = tableService.createRingTable(defaultInput()).table();
        tableService.join(table.id(), "alice", 0);
        var result = tableService.leave(table.id(), "alice", null);
        assertThat(result).isEmpty();
    }

    @Test
    void leaveUnknownNameIsNoOp() {
        var table = tableService.createRingTable(defaultInput()).table();
        tableService.join(table.id(), "alice", 0);
        var result = tableService.leave(table.id(), "nobody", null);
        assertThat(result).hasSize(1);
    }

    // -----------------------------------------------------------------------
    // leave — ハンド進行中は pendingLeave=true
    // -----------------------------------------------------------------------

    @Test
    void leaveDuringActiveHandSetsPendingLeave() {
        var table = tableService.createRingTable(defaultInput()).table();
        tableService.join(table.id(), "alice", 1000);
        tableService.join(table.id(), "bob", 1000);
        tableService.startHand(table.id(), 10); // pot=15 → active

        var members = tableService.leave(table.id(), "alice", null);
        var alice = members.stream().filter(m -> m.name().equals("alice")).findFirst().orElseThrow();
        assertThat(alice.pendingLeave()).isTrue();
    }

    // -----------------------------------------------------------------------
    // processPendingLeaves
    // -----------------------------------------------------------------------

    @Test
    void processPendingLeavesRemovesPendingMembers() {
        var table = tableService.createRingTable(defaultInput()).table();
        tableService.join(table.id(), "alice", 1000);
        tableService.join(table.id(), "bob", 1000);
        tableService.startHand(table.id(), 10);  // hand active

        tableService.leave(table.id(), "alice", null); // pendingLeave=true

        // ハンド終了を模倣してから processPendingLeaves を呼ぶ
        tableService.processPendingLeaves(table.id());

        var members = tableService.getMembers(table.id());
        assertThat(members.stream().noneMatch(m -> m.name().equals("alice"))).isTrue();
        assertThat(members.stream().anyMatch(m -> m.name().equals("bob"))).isTrue();
    }

    @Test
    void processPendingLeavesKeepsNonPendingMembers() {
        var table = tableService.createRingTable(defaultInput()).table();
        tableService.join(table.id(), "alice", 1000);
        tableService.join(table.id(), "bob", 1000);
        tableService.startHand(table.id(), 10);

        // bob だけ pendingLeave
        tableService.leave(table.id(), "bob", null);
        tableService.processPendingLeaves(table.id());

        var members = tableService.getMembers(table.id());
        assertThat(members.stream().anyMatch(m -> m.name().equals("alice"))).isTrue();
        assertThat(members.stream().noneMatch(m -> m.name().equals("bob"))).isTrue();
    }

    @Test
    void processPendingLeavesOnEmptyTableIsNoOp() {
        var table = tableService.createRingTable(defaultInput()).table();
        // 例外なく完了すればよい
        tableService.processPendingLeaves(table.id());
        assertThat(tableService.getMembers(table.id())).isEmpty();
    }

    // -----------------------------------------------------------------------
    // findSeatIndex
    // -----------------------------------------------------------------------

    @Test
    void findSeatIndexReturnsCorrectIndex() {
        var table = tableService.createRingTable(defaultInput()).table();
        var joinResult = tableService.join(table.id(), "alice", 0);
        Integer found = tableService.findSeatIndex(table.id(), "alice");
        assertThat(found).isEqualTo(joinResult.assignedSeatIndex());
    }

    @Test
    void findSeatIndexReturnsNullForUnknownName() {
        var table = tableService.createRingTable(defaultInput()).table();
        assertThat(tableService.findSeatIndex(table.id(), "nobody")).isNull();
    }

    @Test
    void findSeatIndexReturnsNullForBlankName() {
        var table = tableService.createRingTable(defaultInput()).table();
        assertThat(tableService.findSeatIndex(table.id(), "  ")).isNull();
    }

    // -----------------------------------------------------------------------
    // getMembers / getTable
    // -----------------------------------------------------------------------

    @Test
    void getMembersReturnsEmptyListInitially() {
        var table = tableService.createRingTable(defaultInput()).table();
        assertThat(tableService.getMembers(table.id())).isEmpty();
    }

    @Test
    void getTableReturnsCreatedTable() {
        var created = tableService.createRingTable(defaultInput()).table();
        var fetched = tableService.getTable(created.id());
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.name()).isEqualTo(created.name());
    }
}
