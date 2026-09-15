package com.homeserver.core.groceries.infrastructure;

import com.homeserver.core.groceries.internal.dao.GroceryDao;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

@Repository
public class JpaGroceryDao implements GroceryDao {
  private final EntityManager em;

  public JpaGroceryDao(EntityManager em) {
    this.em = em;
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> query(String sql, Object... values) {
    var q = em.createNativeQuery(sql).unwrap(NativeQuery.class);
    for (int i = 0; i < values.length; i++) q.setParameter(i + 1, values[i]);
    q.setTupleTransformer(
        (tuple, aliases) -> {
          Map<String, Object> row = new LinkedHashMap<>();
          for (int i = 0; i < aliases.length; i++) row.put(aliases[i], tuple[i]);
          return row;
        });
    return q.getResultList();
  }

  public int update(String sql, Object... values) {
    var q = em.createNativeQuery(sql);
    for (int i = 0; i < values.length; i++) q.setParameter(i + 1, values[i]);
    return q.executeUpdate();
  }
}
