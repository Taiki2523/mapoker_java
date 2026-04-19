package com.mapoker.domain.game;

import com.mapoker.domain.hand.HandValue;
import java.util.List;

public record ShowdownResult(List<Integer> winnerIndexes, HandValue bestHand, List<Integer> payouts) {}
