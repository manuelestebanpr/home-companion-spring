package com.homeserver.core.groceries.internal.shopping;

import com.homeserver.core.groceries.internal.Quantities;
import com.homeserver.core.groceries.query.*;
import java.math.*;
import java.util.*;

public final class ShoppingListCalculator {
  private ShoppingListCalculator() {}

  public static ShoppingRow calculate(
      UUID item,
      String name,
      BigDecimal target,
      String unit,
      String preferredBrand,
      List<ProductView> products) {
    BigDecimal current =
        products.stream()
            .map(p -> p.packages().multiply(size(p)))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal needed =
        target.multiply(Quantities.factor(unit)).subtract(current).max(BigDecimal.ZERO);
    var preferred =
        products.stream()
            .filter(p -> preferredBrand != null && preferredBrand.equals(p.brand()))
            .toList();
    ProductView suggestion =
        preferred.stream()
            .filter(p -> size(p).compareTo(needed) <= 0)
            .max(Comparator.comparing(ShoppingListCalculator::size))
            .orElseGet(
                () ->
                    preferred.stream()
                        .min(Comparator.comparing(ShoppingListCalculator::size))
                        .orElse(null));
    if (suggestion == null)
      suggestion =
          products.stream()
              .max(
                  Comparator.comparing(
                      ProductView::lastPurchasedAt,
                      Comparator.nullsFirst(Comparator.naturalOrder())))
              .orElse(null);
    BigDecimal buy =
        suggestion == null ? null : needed.divide(size(suggestion), 0, RoundingMode.CEILING);
    return new ShoppingRow(
        item,
        name,
        target,
        current.divide(Quantities.factor(unit)),
        needed.divide(Quantities.factor(unit)),
        unit,
        suggestion == null ? null : suggestion.id(),
        buy,
        suggestion == null);
  }

  private static BigDecimal size(ProductView p) {
    return p.packageQuantity().multiply(Quantities.factor(p.unit()));
  }
}
