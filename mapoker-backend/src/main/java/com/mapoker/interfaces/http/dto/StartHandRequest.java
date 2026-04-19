package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StartHandRequest(@JsonProperty("big_blind") int bigBlind) {}
