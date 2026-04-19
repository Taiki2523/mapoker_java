package com.mapoker.application;

import com.mapoker.domain.rules.ActionType;

public record ActionRecord(int seq, int playerIndex, ActionType actionType, int amount) {}
