package com.homeserver.core.accounts.internal.dao;

import java.util.*;

public interface AccountDao {
  List<Map<String, Object>> query(String sql, Object... values);

  int update(String sql, Object... values);
}
