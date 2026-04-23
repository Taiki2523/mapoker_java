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

@RestController
@RequestMapping("/v1/rooms")
public class RoomController {

    private final TableService tableService;

    public RoomController(TableService tableService) {
        this.tableService = tableService;
    }

    public record MemberRecord(
            String name,
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("joined_at") String joinedAt
    ) {}

    private record MembersResponse(List<MemberRecord> members) {}

    @GetMapping("/{id}/members")
    public MembersResponse getMembers(@PathVariable String id) {
        return new MembersResponse(mapMembers(tableService.getMembers(id)));
    }

    @PostMapping("/{id}/join")
    public MembersResponse join(@PathVariable String id,
                                @Valid @RequestBody(required = false) TableMembershipRequest body,
                                @AuthenticationPrincipal UserDetails principal) {
        String name = principal != null ? principal.getUsername() : body != null ? body.name() : null;
        int buyIn = body != null && body.buyIn() != null ? body.buyIn() : 0;
        return new MembersResponse(mapMembers(tableService.join(id, name, buyIn).members()));
    }

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
