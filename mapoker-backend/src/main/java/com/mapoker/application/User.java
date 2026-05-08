package com.mapoker.application;

import java.time.LocalDateTime;

/**
 * ユーザーの基本情報を表すレコードです。
 *
 * @param id ユーザー ID
 * @param username ユーザー名
 * @param createdAt 作成日時
 */
public record User(long id, String username, LocalDateTime createdAt) {}
