package com.mapoker.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * テーブル参加・退出時の名義指定リクエストです。
 *
 * @param name 参加者名
 * @param buyIn バイイン額
 */
public record TableMembershipRequest(
        @Size(max = 50) String name,
        @JsonProperty("buy_in") @Min(0) Integer buyIn
) {}
