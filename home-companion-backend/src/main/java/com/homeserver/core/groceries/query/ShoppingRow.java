package com.homeserver.core.groceries.query;

import java.math.BigDecimal;
import java.util.UUID;

public record ShoppingRow(
    UUID itemId,
    String item,
    BigDecimal target,
    BigDecimal current,
    BigDecimal needed,
    String unit,
    UUID suggestedProduct,
    BigDecimal packagesToBuy,
    boolean noProductKnown) {}
