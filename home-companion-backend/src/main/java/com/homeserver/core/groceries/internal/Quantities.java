package com.homeserver.core.groceries.internal;

import java.math.BigDecimal;

public final class Quantities {
  private Quantities() {}

  public static BigDecimal positive(BigDecimal value) {
    stored(value);
    if (value.signum() <= 0) throw new IllegalArgumentException("La cantidad debe ser positiva");
    return value;
  }

  public static void stored(BigDecimal value) {
    if (value == null
        || value.stripTrailingZeros().scale() > 3
        || value.abs().compareTo(new BigDecimal("999999999.999")) > 0)
      throw new IllegalArgumentException("Cantidad fuera de rango o con más de 3 decimales");
  }

  public static String kind(String unit) {
    if (unit == null) throw new IllegalArgumentException("Unidad requerida");
    return switch (unit) {
      case "unit" -> "COUNT";
      case "g", "kg" -> "MASS";
      case "ml", "l" -> "VOLUME";
      default -> throw new IllegalArgumentException("Unidad desconocida");
    };
  }

  public static BigDecimal factor(String unit) {
    kind(unit);
    return unit.equals("kg") || unit.equals("l") ? new BigDecimal("1000") : BigDecimal.ONE;
  }

  public static void compatible(String kind, String unit) {
    if (!java.util.Objects.equals(kind, kind(unit)))
      throw new IllegalArgumentException("La unidad no corresponde al tipo de cantidad");
  }
}
