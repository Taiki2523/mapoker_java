package com.mapoker.domain.rules;

public record TableState(Street street, int bigBlind, int currentBet, int lastRaiseSize, boolean raiseOpen) {}
