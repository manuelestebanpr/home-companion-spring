package com.homeserver.controllers.web;

import com.homeserver.core.accounts.api.*;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class AdminWebController {
  private final AccountFacade accounts;

  public AdminWebController(AccountFacade accounts) {
    this.accounts = accounts;
  }

  @GetMapping({"", "/"})
  String users(Model model) {
    model.addAttribute("users", accounts.users());
    model.addAttribute("grants", accounts.grants());
    model.addAttribute("moduleEnabled", accounts.moduleEnabled());
    return "admin";
  }

  @PostMapping("/users")
  String create(@RequestParam String username, @RequestParam String password) {
    accounts.register(username, password);
    return "redirect:/app/keys";
  }

  @PostMapping("/users/{id}")
  String update(
      @PathVariable UUID id,
      @RequestParam String state,
      @RequestParam String role,
      @RequestParam String grant,
      @RequestParam(defaultValue = "/admin") String destination) {
    accounts.update(id, state, role, grant);
    return destination.equals("/app/keys") ? "redirect:/app/keys" : "redirect:/admin";
  }

  @GetMapping("/users/{id}/keys")
  String keys(@PathVariable UUID id, Model model) {
    model.addAttribute("owner", accounts.find(id));
    model.addAttribute("keys", accounts.credentials(id));
    return "admin-keys";
  }

  @PostMapping("/users/{id}/keys/{key}/revoke")
  String revoke(@PathVariable UUID id, @PathVariable UUID key) {
    accounts.revoke(id, key);
    return "redirect:/admin/users/" + id + "/keys";
  }

  @PostMapping("/module")
  String module(@RequestParam boolean enabled) {
    accounts.moduleEnabled(enabled);
    return "redirect:/admin";
  }
}
