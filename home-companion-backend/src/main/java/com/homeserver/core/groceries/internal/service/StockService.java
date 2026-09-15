package com.homeserver.core.groceries.internal.service;

import com.homeserver.core.groceries.internal.Quantities;
import com.homeserver.core.groceries.internal.dao.GroceryDao;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StockService {
  private final GroceryDao dao;

  public StockService(GroceryDao dao) {
    this.dao = dao;
  }

  public void adjust(UUID product, BigDecimal delta) {
    Quantities.stored(delta);
    int changed =
        dao.update(
            "update stock set packages=packages+?1,version=version+1,last_purchased_at=case when ?1>0 then CURRENT_TIMESTAMP else last_purchased_at end where product_id=?2 and packages+?1 between 0 and 999999999.999",
            delta,
            product);
    if (changed != 1)
      throw new IllegalArgumentException("Producto inexistente o existencias fuera de rango");
  }
}
