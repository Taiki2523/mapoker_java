package com.mapoker.interfaces.http;

import com.mapoker.application.TableMemberRecord;
import com.mapoker.application.TableService;
import com.mapoker.application.User;
import com.mapoker.application.UserService;
import com.mapoker.interfaces.http.dto.TableMembershipRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoomController の単体テスト。
 *
 * <p>名義解決の優先順位: body.name() > principal（UserService経由）> null
 */
class RoomControllerTest {

    private TableService tableService;
    private UserService userService;
    private RoomController controller;

    private static final String ROOM_ID = "room-1";
    private static final String ALICE_PUBLIC_ID = "pub-uuid-alice";

    private static final User ALICE = new User(1L, ALICE_PUBLIC_ID, "alice", "0000", null, LocalDateTime.now());

    @BeforeEach
    void setUp() {
        tableService = mock(TableService.class);
        userService = mock(UserService.class);
        controller = new RoomController(tableService, userService);
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
    void joinUsesBodyNameEvenWhenPrincipalPresent() {
        var principal = principalOf(ALICE_PUBLIC_ID);
        var joinResult = new TableService.JoinResult(0, List.of(
                new TableMemberRecord("bodyName", 0, "2024-01-01T00:00:00Z")));
        when(tableService.join(ROOM_ID, "bodyName", 0)).thenReturn(joinResult);

        controller.join(ROOM_ID, new TableMembershipRequest("bodyName", null), principal);

        verify(tableService).join(ROOM_ID, "bodyName", 0);
    }

    @Test
    void joinLooksUpUsernameFromPrincipalWhenBodyHasNoName() {
        var principal = principalOf(ALICE_PUBLIC_ID);
        when(userService.getByPublicId(ALICE_PUBLIC_ID)).thenReturn(ALICE);
        var joinResult = new TableService.JoinResult(0, List.of(
                new TableMemberRecord("alice", 0, "2024-01-01T00:00:00Z")));
        when(tableService.join(ROOM_ID, "alice", 0)).thenReturn(joinResult);

        controller.join(ROOM_ID, new TableMembershipRequest(null, null), principal);

        verify(tableService).join(ROOM_ID, "alice", 0);
    }

    @Test
    void joinUsesBodyNameWhenNoPrincipal() {
        var joinResult = new TableService.JoinResult(1, List.of(
                new TableMemberRecord("bob", 1, "2024-01-01T00:00:00Z")));
        when(tableService.join(ROOM_ID, "bob", 0)).thenReturn(joinResult);

        controller.join(ROOM_ID, new TableMembershipRequest("bob", null), null);

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

        controller.join(ROOM_ID, new TableMembershipRequest("charlie", 500), null);

        verify(tableService).join(ROOM_ID, "charlie", 500);
    }

    @Test
    void joinDefaultsBuyInToZeroWhenBodyBuyInIsNull() {
        var joinResult = new TableService.JoinResult(0, List.of());
        when(tableService.join(ROOM_ID, "dave", 0)).thenReturn(joinResult);

        controller.join(ROOM_ID, new TableMembershipRequest("dave", null), null);

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
    void leaveUsesBodyNameEvenWhenPrincipalPresent() {
        var principal = principalOf(ALICE_PUBLIC_ID);
        when(tableService.leave(ROOM_ID, "bodyName", null)).thenReturn(List.of());

        controller.leave(ROOM_ID, new TableMembershipRequest("bodyName", null), principal);

        verify(tableService).leave(ROOM_ID, "bodyName", null);
    }

    @Test
    void leaveLooksUpUsernameFromPrincipalWhenBodyHasNoName() {
        var principal = principalOf(ALICE_PUBLIC_ID);
        when(userService.getByPublicId(ALICE_PUBLIC_ID)).thenReturn(ALICE);
        when(tableService.leave(ROOM_ID, "alice", null)).thenReturn(List.of());

        controller.leave(ROOM_ID, new TableMembershipRequest(null, null), principal);

        verify(tableService).leave(ROOM_ID, "alice", null);
    }

    @Test
    void leaveUsesBodyNameWhenNoPrincipal() {
        when(tableService.leave(ROOM_ID, "bob", null)).thenReturn(List.of());

        controller.leave(ROOM_ID, new TableMembershipRequest("bob", null), null);

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

        var response = controller.leave(ROOM_ID, new TableMembershipRequest("alice", null), null);

        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).name()).isEqualTo("bob");
    }

    private UserDetails principalOf(String publicId) {
        return new org.springframework.security.core.userdetails.User(publicId, "", List.of());
    }
}
