package com.mapoker.interfaces.http;

import com.mapoker.application.TableMemberRecord;
import com.mapoker.application.TableService;
import com.mapoker.application.User;
import com.mapoker.application.UserService;
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
    private final UserService userService;

    /**
     * @param tableService テーブル管理サービス
     * @param userService  ユーザー管理サービス
     */
    public RoomController(TableService tableService, UserService userService) {
        this.tableService = tableService;
        this.userService = userService;
    }

    /**
     * 参加者表示用 DTO です。
     *
     * @param name        参加者名（シート名）
     * @param seatIndex   着席位置
     * @param joinedAt    参加日時
     * @param displayName 表示名（username#discriminator 形式）
     * @param avatarUrl   アバター画像 URL（null 可）
     */
    public record MemberRecord(
            String name,
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("joined_at") String joinedAt,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("avatar_url") String avatarUrl
    ) {}

    record MembersResponse(List<MemberRecord> members) {}

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
     * @param id        ルーム ID
     * @param body      参加リクエスト（名前・バイイン額）
     * @param principal 認証済みユーザー
     * @return 更新後の参加者一覧
     */
    @PostMapping("/{id}/join")
    public MembersResponse join(@PathVariable String id,
                                @Valid @RequestBody(required = false) TableMembershipRequest body,
                                @AuthenticationPrincipal UserDetails principal) {
        String name = resolveName(principal, body);
        int buyIn = body != null && body.buyIn() != null ? body.buyIn() : 0;
        String[] userInfo = resolveUserInfo(principal, name);
        return new MembersResponse(mapMembers(tableService.join(id, name, buyIn, userInfo[0], userInfo[1], userInfo[2]).members()));
    }

    /**
     * 認証ユーザーまたは指定名義でルームから退出します。
     *
     * @param id        ルーム ID
     * @param body      退出リクエスト
     * @param principal 認証済みユーザー
     * @return 更新後の参加者一覧
     */
    @PostMapping("/{id}/leave")
    public MembersResponse leave(@PathVariable String id,
                                 @Valid @RequestBody(required = false) TableMembershipRequest body,
                                 @AuthenticationPrincipal UserDetails principal) {
        String name = resolveName(principal, body);
        return new MembersResponse(mapMembers(tableService.leave(id, name, null)));
    }

    /**
     * body.name() を優先し、未指定なら principal の publicId からユーザー名を解決します。
     */
    private String resolveName(UserDetails principal, TableMembershipRequest body) {
        if (body != null && body.name() != null && !body.name().isBlank()) {
            return body.name();
        }
        if (principal != null) {
            try {
                return userService.getByPublicId(principal.getUsername()).username();
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 認証ユーザーの {@code [displayName, avatarUrl, publicId]} を返します。未認証時は {@code [name, null, null]}。
     */
    private String[] resolveUserInfo(UserDetails principal, String name) {
        if (principal != null) {
            try {
                User user = userService.getByPublicId(principal.getUsername());
                return new String[]{ user.displayName(), user.avatarUrl(), user.publicId() };
            } catch (Exception ignored) {}
        }
        return new String[]{ name, null, null };
    }

    private List<MemberRecord> mapMembers(List<TableMemberRecord> members) {
        return members.stream()
                .map(member -> new MemberRecord(
                        member.name(), member.seatIndex(), member.joinedAt(),
                        member.displayName(), member.avatarUrl()))
                .toList();
    }
}
