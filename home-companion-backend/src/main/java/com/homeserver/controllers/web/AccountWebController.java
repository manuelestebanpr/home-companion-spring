package com.homeserver.controllers.web;

import com.homeserver.core.accounts.api.*;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AccountWebController {
  private final AccountFacade accounts;

  public AccountWebController(AccountFacade accounts) {
    this.accounts = accounts;
  }

  private Account user(Authentication auth) {
    return ((Identity) auth.getPrincipal()).account();
  }

  @GetMapping("/app")
  String home(Authentication auth) {
    return user(auth).mustChangePassword() ? "redirect:/app/password" : "home";
  }

  @GetMapping("/app/password")
  String password() {
    return "password";
  }

  @PostMapping("/app/password")
  String password(
      Authentication auth,
      @RequestParam String current,
      @RequestParam String replacement,
      jakarta.servlet.http.HttpServletRequest request) {
    accounts.password(user(auth).id(), current, replacement);
    return "redirect:" + LoginDestination.complete(request);
  }

  @GetMapping("/app/keys")
  String keys(Authentication auth, Model model) {
    model.addAttribute("keys", accounts.credentials(user(auth).id()));
    if (user(auth).role().equals("ADMIN")) {
      model.addAttribute("users", accounts.users());
      model.addAttribute("grants", accounts.grants());
    }
    return "keys";
  }

  @PostMapping("/app/keys")
  String create(
      Authentication auth, @RequestParam String label, @RequestParam String cap, Model model) {
    model.addAttribute("secret", accounts.issue(user(auth).id(), "KEY", label, cap));
    return keys(auth, model);
  }

  @PostMapping("/app/keys/{id}/revoke")
  String revoke(Authentication auth, @PathVariable UUID id) {
    accounts.revoke(user(auth).id(), id);
    return "redirect:/app/keys";
  }
}
