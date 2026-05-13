package com.mapoker.interfaces.ws.dto;

import com.mapoker.domain.card.Card;

import java.util.List;

public record HoleCardsPayload(String tableId, int seatIndex, List<Card> hole) {}
