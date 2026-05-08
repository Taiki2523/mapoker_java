package com.mapoker.interfaces.http.dto;

/**
 * ユーザー表示レスポンスです。
 *
 * @param id ユーザー ID
 * @param username ユーザー名
 */
public record UserResponse(long id, String username) {}
