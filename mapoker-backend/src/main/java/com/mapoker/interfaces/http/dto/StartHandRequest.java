package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;

/**
 * ハンド開始リクエストです。
 *
 * @param bigBlind ビッグブラインド額
 */
public record StartHandRequest(@JsonProperty("big_blind") @Positive int bigBlind) {}
