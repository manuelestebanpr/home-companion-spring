package com.homeserver.controllers.phone;

import com.homeserver.core.accounts.api.Identity;
import com.homeserver.core.groceries.command.*;
import com.homeserver.core.groceries.query.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groceries")
public class GroceryApiController {
  public record Adjustment(BigDecimal delta) {}

  public record Target(BigDecimal quantity, String unit, String preferredBrand) {}

  public record Item(String name, String kind, String category) {}

  private final GroceryReadFacade reads;
  private final GroceryWriteFacade writes;

  public GroceryApiController(GroceryReadFacade reads, GroceryWriteFacade writes) {
    this.reads = reads;
    this.writes = writes;
  }

  @GetMapping("/stock")
  Map<String, Object> stock(
      @RequestParam(defaultValue = "") String query,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return Map.of("items", reads.stock(query, limit, offset));
  }

  @GetMapping("/items")
  List<ItemView> items() {
    return reads.items();
  }

  @PostMapping("/items")
  @ResponseStatus(HttpStatus.CREATED)
  Map<String, UUID> item(@RequestBody Item i) {
    return Map.of("id", writes.item(i.name(), i.kind(), i.category()));
  }

  @GetMapping("/desired")
  Map<String, Object> desired() {
    return Map.of("items", reads.desired());
  }

  @GetMapping("/shopping")
  Map<String, Object> shopping(@RequestParam(defaultValue = "false") boolean includeSatisfied) {
    return Map.of("items", reads.shopping(includeSatisfied));
  }

  @PostMapping("/stock")
  ProductView add(
      Authentication auth,
      @RequestBody AddStock command,
      @RequestHeader(name = "Idempotency-Key", required = false) String key) {
    return writes.add(command, ((Identity) auth.getPrincipal()).credentialId().toString(), key);
  }

  @PostMapping("/stock/{id}/adjust")
  ProductView adjust(@PathVariable UUID id, @RequestBody Adjustment input) {
    return writes.adjust(id, input.delta());
  }

  @PutMapping("/desired/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void target(@PathVariable UUID id, @RequestBody Target t) {
    writes.target(id, t.quantity(), t.unit(), t.preferredBrand());
  }

  @DeleteMapping("/desired/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@PathVariable UUID id) {
    writes.removeTarget(id);
  }
}
