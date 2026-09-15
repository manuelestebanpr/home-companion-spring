package com.homeserver.core.groceries.command;

import com.homeserver.core.groceries.query.ProductView;
import java.math.BigDecimal;
import java.util.UUID;

public interface GroceryWriteFacade {
  ProductView add(AddStock command, String scope, String requestId);

  ProductView adjust(UUID product, BigDecimal delta);

  void target(UUID item, BigDecimal quantity, String unit, String preferredBrand);

  UUID item(String name, String kind, String category);

  void removeTarget(UUID item);
}
