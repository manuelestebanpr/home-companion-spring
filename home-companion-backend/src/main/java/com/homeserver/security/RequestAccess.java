package com.homeserver.security;

import com.homeserver.core.accounts.api.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("access")
public class RequestAccess {
  private final AccessFacade access;

  public RequestAccess(AccessFacade access) {
    this.access = access;
  }

  public boolean groceries(Authentication auth, boolean write) {
    return auth != null && auth.getPrincipal() instanceof Identity i && access.allowed(i, write);
  }

  public boolean ready(Authentication auth) {
    return auth != null
        && auth.getPrincipal() instanceof Identity i
        && i.account().state().equals("APPROVED")
        && !i.account().mustChangePassword();
  }

  public boolean admin(Authentication auth) {
    return ready(auth)
        && auth.getPrincipal() instanceof Identity i
        && i.kind().equals("WEB")
        && i.account().role().equals("ADMIN");
  }
}
