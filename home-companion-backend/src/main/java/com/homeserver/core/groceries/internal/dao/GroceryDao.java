package com.homeserver.core.groceries.internal.dao;

import java.util.*;

public interface GroceryDao {
  List<Map<String, Object>> query(String sql, Object... values);

  int update(String sql, Object... values);
}
