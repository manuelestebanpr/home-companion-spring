package com.homeserver.core.accounts.internal.service;

import com.homeserver.core.accounts.api.*;
import com.homeserver.core.accounts.internal.dao.AccountDao;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
  private final AccountDao dao;

  public PermissionService(AccountDao dao) {
    this.dao = dao;
  }

  public boolean allowed(Identity i, boolean write) {
    if (i == null || !i.account().state().equals("APPROVED") || i.account().mustChangePassword())
      return false;
    if (dao.query("select code from modules where code='groceries' and enabled").isEmpty())
      return false;
    var rows =
        dao.query(
            "select permission from user_module_grants where user_id=?1 and module_code='groceries'",
            i.account().id());
    String grant = rows.isEmpty() ? "NONE" : (String) rows.getFirst().get("permission");
    return evaluate(i.kind(), i.account().role(), grant, i.cap(), i.module(), write);
  }

  public static boolean evaluate(
      String kind, String role, String grant, String cap, String module, boolean write) {
    if (kind.equals("KEY"))
      return "groceries".equals(module)
          && (!write || cap.equals("READ_WRITE"))
          && (grant.equals("EDIT") || (!write && grant.equals("VIEW")));
    return role.equals("ADMIN") || grant.equals("EDIT") || (!write && grant.equals("VIEW"));
  }
}
