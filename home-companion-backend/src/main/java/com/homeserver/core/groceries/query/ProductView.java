package com.homeserver.core.groceries.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductView(
    UUID id,
    UUID itemId,
    String item,
    String brand,
    String attributes,
    BigDecimal packageQuantity,
    String unit,
    BigDecimal packages,
    long version,
    Instant lastPurchasedAt,
    String productKey) {}
