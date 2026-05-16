package com.mapoker.interfaces.http;

import com.mapoker.application.TableMemberRecord;
import com.mapoker.application.TableService;
import com.mapoker.interfaces.http.dto.TableMembershipRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoomController の単体テスト。
 *
 * <p>TableService を Mockito でスタブし、Spring を起動せずに検証する。
 * 名義解決（principal → body.name → null）の分岐を網羅する。
 */
class RoomControllerTest {

    private TableService tableService;
    private RoomController controller;

    private static final String ROOM_ID = "room-1";

    @BeforeEach
    void setUp() {
        tableService = mock(TableService.class);
        controller = new RoomController(tableService);
    }

    // -----------------------------------------------------------------------
    // getMembers
    // -----------------------------------------------------------------------

    @Test
    void getMembersReturnsMappedList() {
        when(tableService.getMembers(ROOM_ID)).thenReturn(List.of(
                new TableMemberRecord("alice", 0, "2024-01-01T00:00:00Z"),
                new TableMemberRecord("bob", 1, "2024-01-01T00:01:00Z")
        ));

        var response = controller.getMembers(ROOM_ID);

        assertThat(response.members()).hasSize(2);
        assertThat(response.members().get(0).name()).isEqualTo("alice");
        assertThat(response.members().get(0).seatIndex()).isEqualTo(0);
        assertThat(response.members().get(1).name()).isEqualTo("bob");
    }

    @Test
    void getMembersReturnsEmptyList() {
        when(tableService.getMembers(ROOM_ID)).thenReturn(List.of());

        assertThat(controller.getMembers(ROOM_ID).members()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // join — 名義解決
    // -----------------------------------------------------------------------

    @Test
    void joinUsesAuthenticatedUsernameWhenPrincipalPresent() {
        var principal = new User("alice", "secret", List.of());
        var joinResult = new TableService.JoinResult(0, List.of(
                new TableMemberRecord("alice", 0, "2024-01-01T00:00:00Z")));
        when(tableService.join(ROOM_ID, "alice", 0)).thenReturn(joinResult);

        var body = new TableMembershipRequest("ignored", null);
        controller.join(ROOM_ID, body, principal);

        verify(tableService).join(ROOM_ID, "alice", 0);
    }

    @Test
    void joinUsesBodyNameWhenNoPrincipal() {
        var joinResult = new TableService.JoinResult(1, List.of(
                new TableMemberRecord("bob", 1, "2024-01-01T00:00:00Z")));
        when(tableService.join(ROOM_ID, "bob", 0)).thenReturn(joinResult);

        var body = new TableMembershipRequest("bob", null);
        controller.join(ROOM_ID, body, null);

        verify(tableService).join(ROOM_ID, "bob", 0);
    }

    @Test
    void joinUsesNullNameWhenNoPrincipalAndNoBody() {
        var joinResult = new TableService.JoinResult(0, List.of());
        when(tableService.join(ROOM_ID, null, 0)).thenReturn(joinResult);

        controller.join(ROOM_ID, null, null);

        verify(tableService).join(ROOM_ID, null, 0);
    }

    // -----------------------------------------------------------------------
    // join — buyIn 解決
    // -----------------------------------------------------------------------

    @Test
    void joinUsesBuyInFromBodyWhenProvided() {
        var joinResult = new TableService.JoinResult(0, List.of());
        when(tableService.join(ROOM_ID, "charlie", 500)).thenReturn(joinResult);

        var body = new TableMembershipRequest("charlie", 500);
        controller.join(ROOM_ID, body, null);

        verify(tableService).join(ROOM_ID, "charlie", 500);
    }

    @Test
    void joinDefaultsBuyInToZeroWhenBodyBuyInIsNull() {
        var joinResult = new TableService.JoinResult(0, List.of());
        when(tableService.join(ROOM_ID, "dave", 0)).thenReturn(joinResult);

        var body = new TableMembershipRequest("dave", null);
        controller.join(ROOM_ID, body, null);

        verify(tableService).join(ROOM_ID, "dave", 0);
    }

    // -----------------------------------------------------------------------
    // join — レスポンス
    // -----------------------------------------------------------------------

    @Test
    void joinReturnsMappedMembers() {
        var joinResult = new TableService.JoinResult(0, List.of(
                new TableMemberRecord("alice", 0, "2024-01-01T00:00:00Z")));
        when(tableService.join(ROOM_ID, "alice", 0)).thenReturn(joinResult);

        var response = controller.join(ROOM_ID, new TableMembershipRequest("alice", null), null);

        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).name()).isEqualTo("alice");
        assertThat(response.members().get(0).seatIndex()).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // leave — 名義解決
    // -----------------------------------------------------------------------

    @Test
    void leaveUsesAuthenticatedUsernameWhenPrincipalPresent() {
        var principal = new User("alice", "secret", List.of());
        when(tableService.leave(ROOM_ID, "alice", null)).thenReturn(List.of());

        var body = new TableMembershipRequest("ignored", null);
        controller.leave(ROOM_ID, body, principal);

        verify(tableService).leave(ROOM_ID, "alice", null);
    }

    @Test
    void leaveUsesBodyNameWhenNoPrincipal() {
        when(tableService.leave(ROOM_ID, "bob", null)).thenReturn(List.of());

        var body = new TableMembershipRequest("bob", null);
        controller.leave(ROOM_ID, body, null);

        verify(tableService).leave(ROOM_ID, "bob", null);
    }

    @Test
    void leaveUsesNullNameWhenNoPrincipalAndNoBody() {
        when(tableService.leave(ROOM_ID, null, null)).thenReturn(List.of());

        controller.leave(ROOM_ID, null, null);

        verify(tableService).leave(ROOM_ID, null, null);
    }

    // -----------------------------------------------------------------------
    // leave — レスポンス
    // -----------------------------------------------------------------------

    @Test
    void leaveReturnsMappedMembers() {
        when(tableService.leave(ROOM_ID, "alice", null)).thenReturn(List.of(
                new TableMemberRecord("bob", 1, "2024-01-01T00:00:00Z")));

        var body = new TableMembershipRequest("alice", null);
        var response = controller.leave(ROOM_ID, body, null);

        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).name()).isEqualTo("bob");
    }
}
