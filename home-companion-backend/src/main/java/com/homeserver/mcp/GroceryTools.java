package com.homeserver.mcp;

import com.homeserver.core.groceries.command.*;
import com.homeserver.core.groceries.query.GroceryReadFacade;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class GroceryTools {
  private final GroceryReadFacade reads;
  private final GroceryWriteFacade writes;
  private final JsonMapper json;

  private record InFlight(Semaphore semaphore, int users) {}

  private final ConcurrentHashMap<String, InFlight> active = new ConcurrentHashMap<>();

  public GroceryTools(GroceryReadFacade reads, GroceryWriteFacade writes, JsonMapper json) {
    this.reads = reads;
    this.writes = writes;
    this.json = json;
  }

  @Bean
  List<SyncToolSpecification> groceryToolSpecifications() {
    return List.of(
        tool(
            "groceries_list_stock",
            "Consulta existencias; usa limit y offset para recorrer la despensa.",
            false,
            Map.of(
                "query",
                Map.of("type", "string"),
                "limit",
                Map.of("type", "integer", "minimum", 1, "maximum", 200),
                "offset",
                Map.of("type", "integer", "minimum", 0)),
            List.of(),
            (ctx, a) ->
                Map.of(
                    "items",
                    reads.stock(
                        (String) a.getOrDefault("query", ""),
                        number(a, "limit", 50),
                        number(a, "offset", 0)))),
        tool(
            "groceries_list_desired",
            "Consulta las cantidades objetivo del hogar.",
            false,
            Map.of(),
            List.of(),
            (ctx, a) -> Map.of("items", reads.desired())),
        tool(
            "groceries_shopping_list",
            "Responde qué hay que comprar, con cantidades y paquetes sugeridos.",
            false,
            Map.of("include_satisfied", Map.of("type", "boolean")),
            List.of(),
            (ctx, a) ->
                Map.of("items", reads.shopping(Boolean.TRUE.equals(a.get("include_satisfied"))))),
        tool(
            "groceries_add_stock",
            "Agrega paquetes a un producto existente o nuevo. Indica product_id o new_product. Reutiliza request_id al reintentar.",
            true,
            Map.of(
                "product_id",
                Map.of("type", "string", "format", "uuid"),
                "new_product",
                newProductSchema(),
                "packages",
                Map.of("type", "number", "exclusiveMinimum", 0),
                "request_id",
                Map.of("type", "string", "maxLength", 100)),
            List.of("packages"),
            (ctx, a) ->
                writes.add(
                    new AddStock(
                        a.get("product_id") == null
                            ? null
                            : UUID.fromString((String) a.get("product_id")),
                        a.get("new_product") == null
                            ? null
                            : json.convertValue(a.get("new_product"), AddStock.NewProduct.class),
                        new java.math.BigDecimal(a.get("packages").toString())),
                    (String) ctx.get("credentialScope"),
                    (String) a.get("request_id"))));
  }

  private Map<String, Object> newProductSchema() {
    return Map.of(
        "type",
        "object",
        "additionalProperties",
        false,
        "required",
        List.of("item", "kind", "packageQuantity", "unit"),
        "properties",
        Map.of(
            "item",
            Map.of("type", "string"),
            "kind",
            Map.of("type", "string", "enum", List.of("COUNT", "MASS", "VOLUME")),
            "category",
            Map.of("type", "string"),
            "brand",
            Map.of("type", "string"),
            "attributes",
            Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 10),
            "packageQuantity",
            Map.of("type", "number", "exclusiveMinimum", 0),
            "unit",
            Map.of("type", "string", "enum", List.of("unit", "g", "kg", "ml", "l"))));
  }

  private int number(Map<String, Object> a, String key, int fallback) {
    return a.containsKey(key)
        ? new java.math.BigDecimal(a.get(key).toString()).intValueExact()
        : fallback;
  }

  private SyncToolSpecification tool(
      String name,
      String description,
      boolean write,
      Map<String, Object> properties,
      List<String> required,
      BiFunction<McpTransportContext, Map<String, Object>, Object> action) {
    var definition =
        Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(
                Map.of(
                    "type",
                    "object",
                    "properties",
                    properties,
                    "required",
                    required,
                    "additionalProperties",
                    false))
            .annotations(new ToolAnnotations(name, !write, false, !write, false, false))
            .build();
    return new SyncToolSpecification(
        definition,
        (context, request) -> {
          if (!(context.get("permission") instanceof Predicate<?>))
            return error("Credencial requerida");
          @SuppressWarnings("unchecked")
          var permission = (Predicate<Boolean>) context.get("permission");
          if (!permission.test(write)) return error("Permiso insuficiente para esta herramienta");
          String scope = (String) context.get("credentialScope");
          // Keep counters bounded; idle entries are removed atomically after the last caller exits.
          Semaphore gate =
              active
                  .compute(
                      scope,
                      (k, v) ->
                          v == null
                              ? new InFlight(new Semaphore(4), 1)
                              : new InFlight(v.semaphore(), v.users() + 1))
                  .semaphore();
          if (!gate.tryAcquire()) {
            releaseEntry(scope);
            return error("Máximo 4 operaciones simultáneas por clave");
          }
          try {
            var args = request.arguments() == null ? Map.<String, Object>of() : request.arguments();
            if (!properties.keySet().containsAll(args.keySet())
                || !args.keySet().containsAll(required))
              return error("Argumentos desconocidos o requeridos ausentes");
            Object result = action.apply(context, args);
            return CallToolResult.builder()
                .structuredContent(result)
                .addTextContent(
                    write ? "Existencias actualizadas." : "Consulta de la despensa completada.")
                .isError(false)
                .build();
          } catch (IllegalArgumentException e) {
            return error(e.getMessage());
          } catch (RuntimeException e) {
            return error(
                "No se pudo completar la operación; revisa los datos e inténtalo de nuevo.");
          } finally {
            gate.release();
            releaseEntry(scope);
          }
        });
  }

  private void releaseEntry(String scope) {
    active.computeIfPresent(
        scope, (k, v) -> v.users() == 1 ? null : new InFlight(v.semaphore(), v.users() - 1));
  }

  private static CallToolResult error(String message) {
    return CallToolResult.builder().isError(true).addTextContent(message).build();
  }
}
