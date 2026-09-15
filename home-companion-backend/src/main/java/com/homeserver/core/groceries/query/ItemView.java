package com.homeserver.core.groceries.query;

import java.util.UUID;

public record ItemView(UUID id, String name, String kind, String category) {}
