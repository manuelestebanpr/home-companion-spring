package com.homeserver.core.groceries.internal.service;

import com.homeserver.core.groceries.internal.Quantities;
import com.homeserver.core.groceries.internal.dao.GroceryDao;
import com.homeserver.core.groceries.internal.shopping.ShoppingListCalculator;
import com.homeserver.core.groceries.query.ShoppingRow;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DesiredService {
  private final GroceryDao dao;
  private final ProductService products;

  public DesiredService(GroceryDao dao, ProductService products) {
    this.dao = dao;
    this.products = products;
  }

  public void target(UUID item, BigDecimal quantity, String unit, String preferredBrand) {
    Quantities.positive(quantity);
    var rows = dao.query("select kind from items where id=?1", item);
    if (rows.isEmpty()) throw new IllegalArgumentException("Artículo inexistente");
    Quantities.compatible((String) rows.getFirst().get("kind"), unit);
    UUID brand = null;
    if (preferredBrand != null && !preferredBrand.isBlank()) {
      var brands =
          dao.query("select id from brands where slug=?1", ProductService.slug(preferredBrand));
      if (brands.isEmpty())
        throw new IllegalArgumentException("Marca no encontrada; créala con un producto primero");
      brand = (UUID) brands.getFirst().get("id");
    }
    dao.update(
        "insert into desired_items values (?1,?2,?3,?4) on conflict(item_id) do update set target_qty=excluded.target_qty,unit=excluded.unit,preferred_brand_id=excluded.preferred_brand_id",
        item,
        quantity,
        unit,
        brand);
  }

  public void remove(UUID item) {
    dao.update("delete from desired_items where item_id=?1", item);
  }

  public List<ShoppingRow> rows(boolean all) {
    return dao
        .query(
            "select d.*,i.name,b.name as brand from desired_items d join items i on i.id=d.item_id left join brands b on b.id=d.preferred_brand_id order by i.name")
        .stream()
        .map(
            r ->
                ShoppingListCalculator.calculate(
                    (UUID) r.get("item_id"),
                    (String) r.get("name"),
                    (BigDecimal) r.get("target_qty"),
                    (String) r.get("unit"),
                    (String) r.get("brand"),
                    products.forItem((UUID) r.get("item_id"))))
        .filter(r -> all || r.needed().signum() > 0)
        .toList();
  }
}
