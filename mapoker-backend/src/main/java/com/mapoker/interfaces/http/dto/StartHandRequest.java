package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;

public record StartHandRequest(@JsonProperty("big_blind") @Positive int bigBlind) {}
