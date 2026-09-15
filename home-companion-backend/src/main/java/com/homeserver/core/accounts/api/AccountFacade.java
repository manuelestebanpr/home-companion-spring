package com.homeserver.core.accounts.api;

import java.util.*;

public interface AccountFacade {
  Account login(String username, String password);

  Account register(String username, String password);

  Account find(UUID id);

  List<Account> users();

  void update(UUID id, String state, String role, String grant);

  void password(UUID id, String current, String replacement);

  String issue(UUID user, String kind, String label, String cap);

  List<Map<String, Object>> credentials(UUID user);

  void revoke(UUID user, UUID credential);

  void moduleEnabled(boolean enabled);

  boolean moduleEnabled();

  Map<UUID, String> grants();
}
