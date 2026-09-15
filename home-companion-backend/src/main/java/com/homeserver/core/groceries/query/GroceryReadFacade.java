package com.homeserver.core.groceries.query;

import java.util.*;

public interface GroceryReadFacade {
  List<ProductView> stock(String query, int limit, int offset);

  List<ItemView> items();

  List<ShoppingRow> desired();

  List<ShoppingRow> shopping(boolean includeSatisfied);
}
