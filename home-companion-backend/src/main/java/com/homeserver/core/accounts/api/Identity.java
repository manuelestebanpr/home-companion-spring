package com.homeserver.core.accounts.api;

import java.util.UUID;

public record Identity(
    Account account, UUID credentialId, String kind, String cap, String module) {}
