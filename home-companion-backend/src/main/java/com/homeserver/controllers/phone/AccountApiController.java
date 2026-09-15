package com.homeserver.controllers.phone;

import com.homeserver.core.accounts.api.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountApiController {
  public record Login(String username, String password) {}

  private final AccountFacade accounts;
  private final AccessFacade access;

  public AccountApiController(AccountFacade accounts, AccessFacade access) {
    this.accounts = accounts;
    this.access = access;
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  Account register(@RequestBody Login input) {
    return accounts.register(input.username(), input.password());
  }

  @PostMapping("/auth/login")
  Map<String, Object> login(@RequestBody Login input) {
    Account a;
    try {
      a = accounts.login(input.username(), input.password());
    } catch (IllegalArgumentException e) {
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "invalid-token");
    }
    return Map.of(
        "token",
        accounts.issue(a.id(), "SESSION", "iPhone", "READ_WRITE"),
        "tokenType",
        "Bearer",
        "expiresIn",
        7776000,
        "account",
        a);
  }

  @GetMapping("/me")
  Account me(Authentication auth) {
    return ((Identity) auth.getPrincipal()).account();
  }

  @GetMapping("/features")
  List<Map<String, Object>> features(Authentication auth) {
    return access.features((Identity) auth.getPrincipal());
  }

  @PostMapping("/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(Authentication auth) {
    Identity i = (Identity) auth.getPrincipal();
    accounts.revoke(i.account().id(), i.credentialId());
  }
}
