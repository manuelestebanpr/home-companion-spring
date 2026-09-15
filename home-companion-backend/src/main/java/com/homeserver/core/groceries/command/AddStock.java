package com.homeserver.core.groceries.command;

import java.math.BigDecimal;
import java.util.*;

public record AddStock(UUID productId, NewProduct newProduct, BigDecimal packages) {
  public record NewProduct(
      String item,
      String kind,
      String category,
      String brand,
      List<String> attributes,
      BigDecimal packageQuantity,
      String unit) {}
}
