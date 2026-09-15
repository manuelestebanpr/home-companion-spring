package com.homeserver.core.accounts.api;

import java.util.*;

public interface AccessFacade {
  Identity bearer(String token);

  Identity browser(UUID user);

  boolean allowed(Identity identity, boolean write);

  List<Map<String, Object>> features(Identity identity);
}
