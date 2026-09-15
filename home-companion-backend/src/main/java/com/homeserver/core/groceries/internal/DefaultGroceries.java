package com.homeserver.core.groceries.internal;

import com.homeserver.core.groceries.command.*;
import com.homeserver.core.groceries.internal.dao.GroceryDao;
import com.homeserver.core.groceries.internal.service.*;
import com.homeserver.core.groceries.query.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class DefaultGroceries implements GroceryReadFacade, GroceryWriteFacade {
  private final ProductService products;
  private final StockService stock;
  private final DesiredService desired;
  private final GroceryDao dao;
  private final JsonMapper json = JsonMapper.builder().build();

  public DefaultGroceries(
      ProductService products, StockService stock, DesiredService desired, GroceryDao dao) {
    this.products = products;
    this.stock = stock;
    this.desired = desired;
    this.dao = dao;
  }

  @Transactional(readOnly = true)
  public List<ProductView> stock(String q, int limit, int offset) {
    return products.stock(q, limit, offset);
  }

  @Transactional(readOnly = true)
  public List<ItemView> items() {
    return products.items();
  }

  @Transactional(readOnly = true)
  public List<ShoppingRow> desired() {
    return desired.rows(true);
  }

  @Transactional(readOnly = true)
  public List<ShoppingRow> shopping(boolean all) {
    return desired.rows(all);
  }

  public ProductView add(AddStock c, String scope, String requestId) {
    if (c == null || (c.productId() == null) == (c.newProduct() == null))
      throw new IllegalArgumentException("Indica productId o newProduct, exclusivamente");
    Quantities.positive(c.packages());
    String payload = json.writeValueAsString(c);
    if (requestId != null) {
      if (requestId.isBlank() || requestId.length() > 100)
        throw new IllegalArgumentException("requestId debe tener entre 1 y 100 caracteres");
      // The transaction lock also serializes the first request, before a dedup row exists.
      dao.query(
          "select 1 from pg_advisory_xact_lock(hashtextextended(?1,0))", scope + ":" + requestId);
      dao.update(
          "delete from idempotency_requests where scope=?1 and request_id=?2 and expires_at<=CURRENT_TIMESTAMP",
          scope,
          requestId);
      var rows =
          dao.query(
              "select payload,result from idempotency_requests where scope=?1 and request_id=?2",
              scope,
              requestId);
      if (!rows.isEmpty()) {
        if (!payload.equals(rows.getFirst().get("payload")))
          throw new IllegalArgumentException("requestId ya usado con otro contenido");
        return json.readValue((String) rows.getFirst().get("result"), ProductView.class);
      }
    }
    UUID id = c.productId() == null ? products.resolve(c.newProduct()) : c.productId();
    stock.adjust(id, c.packages());
    ProductView result = products.get(id);
    if (requestId != null)
      dao.update(
          "insert into idempotency_requests values (?1,?2,?3,?4,CURRENT_TIMESTAMP+interval '24 hours')",
          scope,
          requestId,
          payload,
          json.writeValueAsString(result));
    return result;
  }

  public ProductView adjust(UUID id, BigDecimal delta) {
    stock.adjust(id, delta);
    return products.get(id);
  }

  public void target(UUID item, BigDecimal quantity, String unit, String brand) {
    desired.target(item, quantity, unit, brand);
  }

  public UUID item(String name, String kind, String category) {
    return products.item(name, kind, category);
  }

  public void removeTarget(UUID item) {
    desired.remove(item);
  }
}
