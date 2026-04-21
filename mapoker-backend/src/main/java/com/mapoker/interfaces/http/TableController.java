package com.mapoker.interfaces.http;

import com.mapoker.application.TableMemberRecord;
import com.mapoker.application.TableRecord;
import com.mapoker.application.TableService;
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

@RestController
@RequestMapping("/v1/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableResponse createTable(@Valid @RequestBody CreateTableRequest request) {
        TableService.CreateTableResult result = tableService.createRingTable(new TableService.CreateRingTableInput(
                request.tableName(),
                request.playerCount(),
                request.stackSize(),
                request.bigBlind(),
                request.buttonIndex(),
                request.seed(),
                request.oddChipRule(),
                request.visibility(),
                request.flags()
        ));
        return TableResponse.from(result.table(), tableService.getMembers(result.table().id()), GameResponse.from(result.game(), null, false));
    }

    @GetMapping
    public List<TableResponse> listTables(@RequestParam(required = false) String visibility,
                                          @RequestParam(required = false) String flags) {
        return tableService.listTables(visibility, flags).stream()
                .map(table -> TableResponse.from(table, tableService.getMembers(table.id()), null))
                .toList();
    }

    @GetMapping("/{id}")
    public TableResponse getTable(@PathVariable String id) {
        TableRecord table = tableService.getTable(id);
        return TableResponse.from(table, tableService.getMembers(id), GameResponse.from(tableService.getTableGame(id), null, false));
    }

    @GetMapping("/{id}/members")
    public MembersResponse getMembers(@PathVariable String id) {
        return new MembersResponse(toMembers(tableService.getMembers(id)));
    }

    @PostMapping("/{id}/join")
    public MembersResponse join(@PathVariable String id,
                                @Valid @RequestBody(required = false) TableMembershipRequest body,
                                @AuthenticationPrincipal UserDetails principal) {
        String name = principal != null ? principal.getUsername() : body != null ? body.name() : null;
        Integer seatIndex = body != null ? body.seatIndex() : null;
        return new MembersResponse(toMembers(tableService.join(id, name, seatIndex)));
    }

    @PostMapping("/{id}/leave")
    public MembersResponse leave(@PathVariable String id,
                                 @Valid @RequestBody(required = false) TableMembershipRequest body,
                                 @AuthenticationPrincipal UserDetails principal) {
        String name = principal != null ? principal.getUsername() : body != null ? body.name() : null;
        Integer seatIndex = body != null ? body.seatIndex() : null;
        return new MembersResponse(toMembers(tableService.leave(id, name, seatIndex)));
    }

    private List<TableResponse.MemberDto> toMembers(List<TableMemberRecord> members) {
        return members.stream()
                .map(member -> new TableResponse.MemberDto(member.name(), member.seatIndex(), member.joinedAt()))
                .toList();
    }

    public record MembersResponse(List<TableResponse.MemberDto> members) {}
}
