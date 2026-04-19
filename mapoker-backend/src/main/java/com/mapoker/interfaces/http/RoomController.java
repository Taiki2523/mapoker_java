package com.mapoker.interfaces.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapoker.infrastructure.config.GameProperties;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/v1/rooms")
public class RoomController {

    private final Map<String, List<MemberRecord>> rooms = new ConcurrentHashMap<>();
    private final GameProperties gameProperties;

    public RoomController(GameProperties gameProperties) {
        this.gameProperties = gameProperties;
    }

    public record MemberRecord(
            String name,
            @JsonProperty("seat_index") int seatIndex,
            @JsonProperty("joined_at") String joinedAt
    ) {}

    private record MembersResponse(List<MemberRecord> members) {}

    @GetMapping("/{id}/members")
    public MembersResponse getMembers(@PathVariable String id) {
        return new MembersResponse(rooms.getOrDefault(id, List.of()));
    }

    @PostMapping("/{id}/join")
    public MembersResponse join(@PathVariable String id,
                                @RequestBody(required = false) Map<String, Object> body) {
        List<MemberRecord> members = rooms.computeIfAbsent(id, k -> new ArrayList<>());
        String name = body != null && body.containsKey("name")
                ? String.valueOf(body.get("name")) : gameProperties.defaultPlayerName();
        int seatIndex = members.size();
        boolean alreadyJoined = members.stream().anyMatch(m -> m.name().equals(name));
        if (!alreadyJoined) {
            members.add(new MemberRecord(name, seatIndex, Instant.now().toString()));
        }
        return new MembersResponse(List.copyOf(members));
    }

    @PostMapping("/{id}/leave")
    public MembersResponse leave(@PathVariable String id,
                                 @RequestBody(required = false) Map<String, Object> body) {
        String name = body != null && body.containsKey("name")
                ? String.valueOf(body.get("name")) : null;
        List<MemberRecord> members = rooms.getOrDefault(id, new ArrayList<>());
        if (name != null) {
            members.removeIf(m -> m.name().equals(name));
        }
        return new MembersResponse(List.copyOf(members));
    }
}
