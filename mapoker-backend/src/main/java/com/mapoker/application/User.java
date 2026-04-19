package com.mapoker.application;

import java.time.LocalDateTime;

public record User(long id, String username, LocalDateTime createdAt) {}
