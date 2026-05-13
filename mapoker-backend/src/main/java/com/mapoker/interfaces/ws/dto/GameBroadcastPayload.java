package com.mapoker.interfaces.ws.dto;

import com.mapoker.interfaces.http.dto.GameResponse;

import java.time.Instant;

public record GameBroadcastPayload(GameResponse game, Instant streetRevealedAt) {}
