package com.homeserver.controllers.web;

import com.homeserver.core.accounts.api.Identity;
import com.homeserver.core.groceries.command.*;
import com.homeserver.core.groceries.query.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/app/groceries")
public class GroceryWebController {
  private final GroceryReadFacade reads;
  private final GroceryWriteFacade writes;

  public GroceryWebController(GroceryReadFacade reads, GroceryWriteFacade writes) {
    this.reads = reads;
    this.writes = writes;
  }

  @GetMapping({"", "/"})
  String stock(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int offset,
      Model model) {
    model.addAttribute("products", reads.stock(q, 50, offset));
    model.addAttribute("q", q);
    model.addAttribute("offset", offset);
    return "stock";
  }

  @GetMapping("/new")
  String create(Model model) {
    model.addAttribute("requestId", UUID.randomUUID());
    return "new-product";
  }

  @PostMapping("/new")
  String create(
      Authentication auth,
      @RequestParam String item,
      @RequestParam String kind,
      @RequestParam(defaultValue = "General") String category,
      @RequestParam(defaultValue = "") String brand,
      @RequestParam(defaultValue = "") String attributes,
      @RequestParam BigDecimal packageQuantity,
      @RequestParam String unit,
      @RequestParam BigDecimal packages,
      @RequestParam String requestId) {
    var attrs =
        Arrays.stream(attributes.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    writes.add(
        new AddStock(
            null,
            new AddStock.NewProduct(item, kind, category, brand, attrs, packageQuantity, unit),
            packages),
        ((Identity) auth.getPrincipal()).account().id().toString(),
        requestId);
    return "redirect:/app/groceries";
  }

  @PostMapping("/{id}/adjust")
  String adjust(@PathVariable UUID id, @RequestParam BigDecimal delta) {
    writes.adjust(id, delta);
    return "redirect:/app/groceries";
  }

  @GetMapping("/shopping")
  String shopping(Model model) {
    model.addAttribute("rows", reads.shopping(false));
    return "shopping";
  }

  @GetMapping("/targets")
  String targets(Model model) {
    model.addAttribute("items", reads.items());
    model.addAttribute("rows", reads.desired());
    return "targets";
  }

  @PostMapping("/targets")
  String target(
      @RequestParam UUID item,
      @RequestParam BigDecimal quantity,
      @RequestParam String unit,
      @RequestParam(defaultValue = "") String preferredBrand) {
    writes.target(item, quantity, unit, preferredBrand);
    return "redirect:/app/groceries/targets";
  }

  @PostMapping("/targets/{id}/remove")
  String remove(@PathVariable UUID id) {
    writes.removeTarget(id);
    return "redirect:/app/groceries/targets";
  }

  @PostMapping("/items")
  String item(
      @RequestParam String name,
      @RequestParam String kind,
      @RequestParam(defaultValue = "General") String category) {
    writes.item(name, kind, category);
    return "redirect:/app/groceries/targets";
  }
}
