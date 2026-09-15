package com.homeserver.core.groceries.internal.service;

import com.homeserver.core.groceries.command.AddStock.NewProduct;
import com.homeserver.core.groceries.internal.Quantities;
import com.homeserver.core.groceries.internal.dao.GroceryDao;
import com.homeserver.core.groceries.query.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
  private final GroceryDao dao;

  public ProductService(GroceryDao dao) {
    this.dao = dao;
  }

  public static String slug(String text) {
    if (text == null || text.isBlank() || text.length() > 80)
      throw new IllegalArgumentException("Nombre requerido, máximo 80 caracteres");
    String s =
        Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    if (s.isEmpty()) throw new IllegalArgumentException("El nombre debe contener letras o números");
    return s;
  }

  public UUID item(String name, String kind, String category) {
    if (kind == null || !Set.of("COUNT", "MASS", "VOLUME").contains(kind))
      throw new IllegalArgumentException("Tipo de cantidad inválido");
    if (category == null || category.isBlank()) category = "General";
    if (category.length() > 80) throw new IllegalArgumentException("Categoría demasiado larga");
    String key = slug(name);
    dao.update(
        "insert into items values (?1,?2,?3,?4,?5) on conflict(slug) do nothing",
        UUID.randomUUID(),
        name.trim(),
        key,
        kind,
        category);
    var row = dao.query("select * from items where slug=?1", key).getFirst();
    if (!row.get("kind").equals(kind))
      throw new IllegalArgumentException("El tipo de cantidad del artículo está fijado");
    return (UUID) row.get("id");
  }

  private UUID vocabulary(String table, String name) {
    String key = slug(name);
    dao.update(
        "insert into " + table + " values (?1,?2,?3) on conflict(slug) do nothing",
        UUID.randomUUID(),
        name.trim(),
        key);
    return (UUID) dao.query("select id from " + table + " where slug=?1", key).getFirst().get("id");
  }

  public UUID resolve(NewProduct p) {
    if (p == null) throw new IllegalArgumentException("Producto requerido");
    Quantities.positive(p.packageQuantity());
    Quantities.compatible(p.kind(), p.unit());
    UUID item = item(p.item(), p.kind(), p.category());
    UUID brand = p.brand() == null || p.brand().isBlank() ? null : vocabulary("brands", p.brand());
    var attributes = p.attributes() == null ? List.<String>of() : p.attributes();
    if (attributes.size() > 10) throw new IllegalArgumentException("Máximo 10 atributos");
    var ids = new TreeMap<String, UUID>();
    for (String attr : attributes) ids.put(slug(attr), vocabulary("attributes", attr));
    String variant = String.join("+", ids.keySet());
    if (variant.length() > 400)
      throw new IllegalArgumentException("Los atributos combinados superan 400 caracteres");
    String key =
        slug(p.item())
            + "/"
            + (brand == null ? "generico" : slug(p.brand()))
            + "/"
            + (variant.isEmpty() ? "none" : variant)
            + "/"
            + p.packageQuantity().stripTrailingZeros().toPlainString()
            + "-"
            + p.unit();
    UUID id = UUID.randomUUID();
    int created =
        dao.update(
            "insert into products values (?1,?2,?3,?4,?5,?6,?7) on conflict(product_key) do nothing",
            id,
            item,
            brand,
            variant,
            p.packageQuantity(),
            p.unit(),
            key);
    if (created == 0)
      return (UUID)
          dao.query("select id from products where product_key=?1", key).getFirst().get("id");
    for (UUID attr : ids.values())
      dao.update("insert into product_attributes values (?1,?2)", id, attr);
    dao.update("insert into stock(product_id) values (?1)", id);
    return id;
  }

  private static final String SELECT =
      "select p.*,i.name as item,b.name as brand,s.packages,s.version,s.last_purchased_at from products p join items i on i.id=p.item_id left join brands b on b.id=p.brand_id join stock s on s.product_id=p.id ";

  public List<ProductView> stock(String query, int limit, int offset) {
    if (limit < 1 || limit > 200 || offset < 0 || offset > 100000)
      throw new IllegalArgumentException("Paginación inválida");
    if (query == null) query = "";
    if (query.length() > 80) throw new IllegalArgumentException("Búsqueda demasiado larga");
    return dao
        .query(
            SELECT
                + "where position(lower(?1) in lower(i.name||' '||coalesce(b.name,'')||' '||p.variant_key))>0 order by i.name,p.id limit ?2 offset ?3",
            query,
            limit,
            offset)
        .stream()
        .map(this::map)
        .toList();
  }

  public ProductView get(UUID id) {
    return dao.query(SELECT + "where p.id=?1", id).stream()
        .findFirst()
        .map(this::map)
        .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));
  }

  public List<ProductView> forItem(UUID id) {
    return dao.query(SELECT + "where p.item_id=?1 order by p.id", id).stream()
        .map(this::map)
        .toList();
  }

  private ProductView map(Map<String, Object> r) {
    Object at = r.get("last_purchased_at");
    Instant instant =
        at == null
            ? null
            : at instanceof Instant i
                ? i
                : at instanceof OffsetDateTime o
                    ? o.toInstant()
                    : ((java.sql.Timestamp) at).toInstant();
    return new ProductView(
        (UUID) r.get("id"),
        (UUID) r.get("item_id"),
        (String) r.get("item"),
        (String) r.get("brand"),
        (String) r.get("variant_key"),
        (BigDecimal) r.get("package_qty"),
        (String) r.get("unit"),
        (BigDecimal) r.get("packages"),
        ((Number) r.get("version")).longValue(),
        instant,
        (String) r.get("product_key"));
  }

  public List<ItemView> items() {
    return dao.query("select * from items order by name").stream()
        .map(
            r ->
                new ItemView(
                    (UUID) r.get("id"),
                    (String) r.get("name"),
                    (String) r.get("kind"),
                    (String) r.get("category")))
        .toList();
  }
}
