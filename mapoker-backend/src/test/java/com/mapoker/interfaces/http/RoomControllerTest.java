package com.mapoker.interfaces.http;

import com.mapoker.application.table.TableMemberRecord;
import com.mapoker.application.table.TableLifecycleService;
import com.mapoker.application.table.TableMembershipService;
import com.mapoker.application.table.TableQueryService;
import com.mapoker.application.auth.User;
import com.mapoker.application.auth.UserService;
import com.mapoker.interfaces.http.dto.TableMembershipRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoomController の単体テスト。
 *
 * <p>名義解決の優先順位: body.name() > principal（UserService経由）> null
 */
class RoomControllerTest {

    private TableQueryService queryService;
    private TableMembershipService membershipService;
    private UserService userService;
    private RoomController controller;

    private static final String ROOM_ID = "room-1";
    private static final String ALICE_PUBLIC_ID = "pub-uuid-alice";

    private static final User ALICE = new User(1L, ALICE_PUBLIC_ID, "alice", "0000", null, LocalDateTime.now());

    @BeforeEach
    void setUp() {
        queryService = mock(TableQueryService.class);
        membershipService = mock(TableMembershipService.class);
        userService = mock(UserService.class);
        controller = new RoomController(queryService, membershipService, userService);
    }

    @Test
    void getMembersReturnsMappedList() {
        when(queryService.getMembers(ROOM_ID)).thenReturn(List.of(
                new TableMemberRecord("alice", 0, "2024-01-01T00:00:00Z"),
                new TableMemberRecord("bob", 1, "2024-01-01T00:01:00Z")
        ));

        var response = controller.getMembers(ROOM_ID);

        assertThat(response.members()).hasSize(2);
        assertThat(response.members().get(0).name()).isEqualTo("alice");
        assertThat(response.members().get(1).name()).isEqualTo("bob");
    }

    @Test
    void getMembersReturnsEmptyList() {
        when(queryService.getMembers(ROOM_ID)).thenReturn(List.of());
        assertThat(controller.getMembers(ROOM_ID).members()).isEmpty();
    }

    @Test
    void joinIgnoresBodyNameWhenPrincipalPresent() {
        var principal = principalOf(ALICE_PUBLIC_ID);
        when(userService.getByPublicId(ALICE_PUBLIC_ID)).thenReturn(ALICE);
        var joinResult = new TableMembershipService.JoinResult(0, List.of(
                new TableMemberRecord("alice", 0, "2024-01-01T00:00:00Z")));
        when(membershipService.join(eq(ROOM_ID), eq("alice"), eq(0), any(), any(), any())).thenReturn(joinResult);

        // body に別名を入れても principal のユーザー名が使われる（席乗っ取り防止）
        controller.join(ROOM_ID, new TableMembershipRequest("bodyName", null), principal);

        verify(membershipService).join(eq(ROOM_ID), eq("alice"), eq(0), any(), any(), any());
    }

    @Test
    void joinLooksUpUsernameFromPrincipalWhenBodyHasNoName() {
        var principal = principalOf(ALICE_PUBLIC_ID);
        when(userService.getByPublicId(ALICE_PUBLIC_ID)).thenReturn(ALICE);
        var joinResult = new TableMembershipService.JoinResult(0, List.of(
                new TableMemberRecord("alice", 0, "2024-01-01T00:00:00Z")));
        when(membershipService.join(eq(ROOM_ID), eq("alice"), eq(0), any(), any(), any())).thenReturn(joinResult);

        controller.join(ROOM_ID, new TableMembershipRequest(null, null), principal);

        verify(membershipService).join(eq(ROOM_ID), eq("alice"), eq(0), any(), any(), any());
    }

    @Test
    void joinUsesBodyNameWhenNoPrincipal() {
        var joinResult = new TableMembershipService.JoinResult(1, List.of(
                new TableMemberRecord("bob", 1, "2024-01-01T00:00:00Z")));
        when(membershipService.join(eq(ROOM_ID), eq("bob"), eq(0), any(), any(), any())).thenReturn(joinResult);

        controller.join(ROOM_ID, new TableMembershipRequest("bob", null), null);

        verify(membershipService).join(eq(ROOM_ID), eq("bob"), eq(0), any(), any(), any());
    }

    @Test
    void joinUsesNullNameWhenNoPrincipalAndNoBody() {
        var joinResult = new TableMembershipService.JoinResult(0, List.of());
        when(membershipService.join(eq(ROOM_ID), eq(null), eq(0), any(), any(), any())).thenReturn(joinResult);

        controller.join(ROOM_ID, null, null);

        verify(membershipService).join(eq(ROOM_ID), eq(null), eq(0), any(), any(), any());
    }

    @Test
    void joinUsesBuyInFromBodyWhenProvided() {
        var joinResult = new TableMembershipService.JoinResult(0, List.of());
        when(membershipService.join(eq(ROOM_ID), eq("charlie"), eq(500), any(), any(), any())).thenReturn(joinResult);

        controller.join(ROOM_ID, new TableMembershipRequest("charlie", 500), null);

        verify(membershipService).join(eq(ROOM_ID), eq("charlie"), eq(500), any(), any(), any());
    }

    @Test
    void joinDefaultsBuyInToZeroWhenBodyBuyInIsNull() {
        var joinResult = new TableMembershipService.JoinResult(0, List.of());
        when(membershipService.join(eq(ROOM_ID), eq("dave"), eq(0), any(), any(), any())).thenReturn(joinResult);

        controller.join(ROOM_ID, new TableMembershipRequest("dave", null), null);

        verify(membershipService).join(eq(ROOM_ID), eq("dave"), eq(0), any(), any(), any());
    }

    @Test
    void joinReturnsMappedMembers() {
        var joinResult = new TableMembershipService.JoinResult(0, List.of(
                new TableMemberRecord("alice", 0, "2024-01-01T00:00:00Z")));
        when(membershipService.join(eq(ROOM_ID), eq("alice"), eq(0), any(), any(), any())).thenReturn(joinResult);

        var response = controller.join(ROOM_ID, new TableMembershipRequest("alice", null), null);

        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).name()).isEqualTo("alice");
        assertThat(response.members().get(0).seatIndex()).isEqualTo(0);
    }

    @Test
    void leaveIgnoresBodyNameWhenPrincipalPresent() {
        var principal = principalOf(ALICE_PUBLIC_ID);
        when(userService.getByPublicId(ALICE_PUBLIC_ID)).thenReturn(ALICE);
        when(membershipService.leave(ROOM_ID, "alice", null, ALICE_PUBLIC_ID)).thenReturn(List.of());

        // body に別名を入れても principal のユーザー名が使われる
        controller.leave(ROOM_ID, new TableMembershipRequest("bodyName", null), principal);

        verify(membershipService).leave(ROOM_ID, "alice", null, ALICE_PUBLIC_ID);
    }

    @Test
    void leaveLooksUpUsernameFromPrincipalWhenBodyHasNoName() {
        var principal = principalOf(ALICE_PUBLIC_ID);
        when(userService.getByPublicId(ALICE_PUBLIC_ID)).thenReturn(ALICE);
        when(membershipService.leave(ROOM_ID, "alice", null, ALICE_PUBLIC_ID)).thenReturn(List.of());

        controller.leave(ROOM_ID, new TableMembershipRequest(null, null), principal);

        verify(membershipService).leave(ROOM_ID, "alice", null, ALICE_PUBLIC_ID);
    }

    @Test
    void leaveUsesBodyNameWhenNoPrincipal() {
        when(membershipService.leave(ROOM_ID, "bob", null, null)).thenReturn(List.of());

        controller.leave(ROOM_ID, new TableMembershipRequest("bob", null), null);

        verify(membershipService).leave(ROOM_ID, "bob", null, null);
    }

    @Test
    void leaveUsesNullNameWhenNoPrincipalAndNoBody() {
        when(membershipService.leave(ROOM_ID, null, null, null)).thenReturn(List.of());

        controller.leave(ROOM_ID, null, null);

        verify(membershipService).leave(ROOM_ID, null, null, null);
    }

    @Test
    void leaveReturnsMappedMembers() {
        when(membershipService.leave(ROOM_ID, "alice", null, null)).thenReturn(List.of(
                new TableMemberRecord("bob", 1, "2024-01-01T00:00:00Z")));

        var response = controller.leave(ROOM_ID, new TableMembershipRequest("alice", null), null);

        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).name()).isEqualTo("bob");
    }

    private UserDetails principalOf(String publicId) {
        return new org.springframework.security.core.userdetails.User(publicId, "", List.of());
    }
}
