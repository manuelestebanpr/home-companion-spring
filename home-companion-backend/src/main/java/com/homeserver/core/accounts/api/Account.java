package com.homeserver.core.accounts.api;

import java.util.UUID;

public record Account(
    UUID id, String username, String state, String role, boolean mustChangePassword) {}
