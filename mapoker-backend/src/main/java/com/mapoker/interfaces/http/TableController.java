package com.mapoker.interfaces.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.application.table.TableMemberRecord;
import com.mapoker.application.table.TableRecord;
import com.mapoker.application.table.TableService;
import com.mapoker.application.auth.UserService;
import com.mapoker.interfaces.http.dto.CreateTableRequest;
import com.mapoker.interfaces.http.dto.GameResponse;
import com.mapoker.interfaces.http.dto.TableMembershipRequest;
import com.mapoker.interfaces.http.dto.TableResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Spring の {@code @RestController} としてテーブル管理 API を提供するコントローラです。
 */
@RestController
@RequestMapping("/v1/tables")
public class TableController {

    private final TableService tableService;
    private final UserService userService;

    public TableController(TableService tableService, UserService userService) {
        this.tableService = tableService;
        this.userService = userService;
    }

    /**
     * 新しいテーブルを作成します。
     *
     * @param request テーブル作成条件
     * @return 作成結果
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse createTable(@Valid @RequestBody CreateTableRequest request) {
        TableService.CreateTableResult result = tableService.createRingTable(new TableService.CreateRingTableInput(
                request.tableName(),
                request.playerCount(),
                request.smallBlind(),
                request.bigBlind(),
                request.visibility(),
                request.flags(),
                request.ante(),
                request.straddleEnabled()
        ));
        return TableResponse.from(result.table(), tableService.getMembers(result.table().id()), GameResponse.from(result.game(), null, false));
    }

    /**
     * 条件に一致するテーブル一覧を取得します。
     *
     * @param visibility 公開設定の絞り込み条件
     * @param flags 必須フラグの絞り込み条件
     * @return テーブル一覧
     */
    @GetMapping
    public List<TableResponse> listTables(@RequestParam(required = false) String visibility,
                                          @RequestParam(required = false) String flags) {
        return tableService.listTables(visibility, flags).stream()
                .map(table -> TableResponse.from(table, tableService.getMembers(table.id()), null))
                .toList();
    }

    /**
     * 指定テーブルの詳細を取得します。
     *
     * @param id テーブル ID
     * @return テーブル詳細
     */
    @GetMapping("/{id}")
    public TableResponse getTable(@PathVariable String id) {
        TableRecord table = tableService.getTable(id);
        return TableResponse.from(table, tableService.getMembers(id), GameResponse.from(tableService.getTableGame(id), null, false));
    }

    /**
     * 指定テーブルの参加者一覧を取得します。
     *
     * @param id テーブル ID
     * @return 参加者一覧
     */
    @GetMapping("/{id}/members")
    public MembersResponse getMembers(@PathVariable String id) {
        return new MembersResponse(toMembers(tableService.getMembers(id)));
    }

    /**
     * 認証ユーザーまたは指定名義でテーブルへ参加します。
     *
     * @param id テーブル ID
     * @param body 参加リクエスト
     * @param principal 認証済みユーザー
     * @return 割り当て席と参加者一覧
     */
    @PostMapping("/{id}/join")
    public JoinResponse join(@PathVariable String id,
                             @Valid @RequestBody(required = false) TableMembershipRequest body,
                             @AuthenticationPrincipal UserDetails principal) {
        String name = resolveName(principal, body);
        int buyIn = body != null && body.buyIn() != null ? body.buyIn() : 0;
        String[] userInfo = resolveUserInfo(principal, name);
        TableService.JoinResult result = tableService.join(id, name, buyIn, userInfo[0], userInfo[1], userInfo[2]);
        return new JoinResponse(result.assignedSeatIndex(), toMembers(result.members()));
    }

    /**
     * 認証ユーザーまたは指定名義でテーブルから退出します。
     *
     * @param id テーブル ID
     * @param body 退出リクエスト
     * @param principal 認証済みユーザー
     * @return 更新後の参加者一覧
     */
    @PostMapping("/{id}/leave")
    public MembersResponse leave(@PathVariable String id,
                                 @Valid @RequestBody(required = false) TableMembershipRequest body,
                                 @AuthenticationPrincipal UserDetails principal) {
        String name = resolveName(principal, body);
        String publicId = principal != null ? principal.getUsername() : null;
        return new MembersResponse(toMembers(tableService.leave(id, name, null, publicId)));
    }

    /** [displayName, avatarUrl] を返す。未認証時または解決失敗時は [name, null]。 */
    private String[] resolveUserInfo(UserDetails principal, String name) {
        if (principal != null) {
            try {
                var user = userService.getByPublicId(principal.getUsername());
                return new String[]{ user.displayName(), user.avatarUrl(), user.publicId() };
            } catch (Exception ignored) {}
        }
        return new String[]{ name, null, null };
    }

    private String resolveName(UserDetails principal, TableMembershipRequest body) {
        if (body != null && body.name() != null && !body.name().isBlank()) return body.name();
        if (principal != null) {
            try {
                return userService.getByPublicId(principal.getUsername()).username();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<TableResponse.MemberDto> toMembers(List<TableMemberRecord> members) {
        return members.stream()
                .map(member -> new TableResponse.MemberDto(member.name(), member.seatIndex(), member.joinedAt(),
                        member.pendingLeave(), member.displayName(), member.avatarUrl()))
                .toList();
    }

    /**
     * 参加者一覧レスポンスです。
     *
     * @param members 参加者一覧
     */
    public record MembersResponse(List<TableResponse.MemberDto> members) {}

    /**
     * 参加結果レスポンスです。
     *
     * @param assignedSeatIndex 割り当て席番号
     * @param members 更新後の参加者一覧
     */
    public record JoinResponse(
            @JsonProperty("assigned_seat_index") int assignedSeatIndex,
            List<TableResponse.MemberDto> members
    ) {}
}
