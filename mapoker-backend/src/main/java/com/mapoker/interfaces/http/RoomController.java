package com.mapoker.interfaces.http;

import com.mapoker.application.TableMemberRecord;
import com.mapoker.application.TableService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.interfaces.http.dto.TableMembershipRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Spring の {@code @RestController} としてルーム参加者 API を提供するコントローラです。
 */
@RestController
@RequestMapping("/v1/rooms")
public class RoomController {

    private final TableService tableService;

    public RoomController(TableService tableService) {
        this.tableService = tableService;
    }

    /**
     * 参加者表示用 DTO です。
     *
     * @param name 参加者名
     * @param seatIndex 着席位置
     * @param joinedAt 参加日時
     */
    public record MemberRecord(
            String name,
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("joined_at") String joinedAt
    ) {}

    private record MembersResponse(List<MemberRecord> members) {}

    /**
     * ルーム参加者一覧を取得します。
     *
     * @param id ルーム ID
     * @return 参加者一覧
     */
    @GetMapping("/{id}/members")
    public MembersResponse getMembers(@PathVariable String id) {
        return new MembersResponse(mapMembers(tableService.getMembers(id)));
    }

    /**
     * 認証ユーザーまたは指定名義でルームへ参加します。
     *
     * @param id ルーム ID
     * @param body 参加リクエスト
     * @param principal 認証済みユーザー
     * @return 更新後の参加者一覧
     */
    @PostMapping("/{id}/join")
    public MembersResponse join(@PathVariable String id,
                                @Valid @RequestBody(required = false) TableMembershipRequest body,
                                @AuthenticationPrincipal UserDetails principal) {
        String name = principal != null ? principal.getUsername() : body != null ? body.name() : null;
        int buyIn = body != null && body.buyIn() != null ? body.buyIn() : 0;
        return new MembersResponse(mapMembers(tableService.join(id, name, buyIn).members()));
    }

    /**
     * 認証ユーザーまたは指定名義でルームから退出します。
     *
     * @param id ルーム ID
     * @param body 退出リクエスト
     * @param principal 認証済みユーザー
     * @return 更新後の参加者一覧
     */
    @PostMapping("/{id}/leave")
    public MembersResponse leave(@PathVariable String id,
                                 @Valid @RequestBody(required = false) TableMembershipRequest body,
                                 @AuthenticationPrincipal UserDetails principal) {
        String name = principal != null ? principal.getUsername() : body != null ? body.name() : null;
        return new MembersResponse(mapMembers(tableService.leave(id, name, null)));
    }

    private List<MemberRecord> mapMembers(List<TableMemberRecord> members) {
        return members.stream()
                .map(member -> new MemberRecord(member.name(), member.seatIndex(), member.joinedAt()))
                .toList();
    }
}
