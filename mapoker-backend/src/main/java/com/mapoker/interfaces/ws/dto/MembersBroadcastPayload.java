package com.mapoker.interfaces.ws.dto;

import com.mapoker.interfaces.http.dto.TableResponse;

import java.util.List;

public record MembersBroadcastPayload(String tableId, List<TableResponse.MemberDto> members) {}
