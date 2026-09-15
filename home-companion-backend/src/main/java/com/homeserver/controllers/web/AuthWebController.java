package com.homeserver.controllers.web;

import com.homeserver.core.accounts.api.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthWebController {
  private final AccountFacade accounts;

  public AuthWebController(AccountFacade accounts) {
    this.accounts = accounts;
  }

  @GetMapping("/")
  String root() {
    return "redirect:/app";
  }

  @GetMapping({"/app/login", "/admin/login"})
  String login(HttpServletRequest request, Model model, Authentication authentication) {
    String destination = request.getRequestURI().equals("/admin/login") ? "/admin" : "/app";
    if (authentication != null && authentication.getPrincipal() instanceof Identity identity) {
      LoginDestination.remember(request, destination);
      return identity.account().mustChangePassword()
          ? "redirect:/app/password"
          : "redirect:" + LoginDestination.complete(request);
    }
    model.addAttribute("destination", destination);
    model.addAttribute("adminLogin", destination.equals("/admin"));
    return "login";
  }

  @GetMapping("/app/register")
  String register() {
    return "register";
  }

  @PostMapping("/app/login")
  String login(
      @RequestParam String username,
      @RequestParam String password,
      @RequestParam(defaultValue = "/app") String destination,
      HttpServletRequest request) {
    Account account;
    try {
      account = accounts.login(username, password);
    } catch (IllegalArgumentException e) {
      return "redirect:"
          + (LoginDestination.safe(destination).equals("/admin") ? "/admin/login" : "/app/login")
          + "?error";
    }
    request.getSession(true);
    request.changeSessionId();
    request.getSession().setAttribute("HC_USER", account.id());
    LoginDestination.remember(request, destination);
    return account.mustChangePassword()
        ? "redirect:/app/password"
        : "redirect:" + LoginDestination.complete(request);
  }

  @PostMapping("/app/register")
  String register(@RequestParam String username, @RequestParam String password) {
    accounts.register(username, password);
    return "redirect:/app/login?registered";
  }

  @PostMapping("/app/logout")
  String logout(HttpServletRequest request) {
    var s = request.getSession(false);
    if (s != null) s.invalidate();
    return "redirect:/app/login";
  }
}
