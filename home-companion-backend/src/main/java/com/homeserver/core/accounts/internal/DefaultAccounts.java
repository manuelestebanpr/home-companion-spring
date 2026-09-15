package com.homeserver.core.accounts.internal;

import com.homeserver.core.accounts.api.*;
import com.homeserver.core.accounts.internal.dao.AccountDao;
import com.homeserver.core.accounts.internal.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DefaultAccounts implements AccountFacade, AccessFacade {
  private final AccountDao dao;
  private final IdentityService identities;
  private final CredentialService tokens;
  private final PermissionService permissions;

  public DefaultAccounts(
      AccountDao dao,
      IdentityService identities,
      CredentialService tokens,
      PermissionService permissions) {
    this.dao = dao;
    this.identities = identities;
    this.tokens = tokens;
    this.permissions = permissions;
  }

  public Account login(String u, String p) {
    return identities.login(u, p);
  }

  public Account register(String u, String p) {
    return identities.create(u, p, "PENDING", "MEMBER", false);
  }

  public Account find(UUID id) {
    return identities.find(id);
  }

  public List<Account> users() {
    return dao.query("select * from users order by username").stream()
        .map(identities::map)
        .toList();
  }

  public void update(UUID id, String state, String role, String grant) {
    if (!Set.of("PENDING", "APPROVED", "REJECTED", "DISABLED").contains(state)
        || !Set.of("ADMIN", "MEMBER").contains(role)
        || !Set.of("NONE", "VIEW", "EDIT").contains(grant))
      throw new IllegalArgumentException("Estado o permiso inválido");
    // Serialize administrator changes so concurrent requests cannot remove the last admin.
    dao.query("select id from users where role='ADMIN' order by id for update");
    Account old = find(id);
    boolean transition =
        old.state().equals(state)
            || switch (old.state()) {
              case "PENDING" -> Set.of("APPROVED", "REJECTED").contains(state);
              case "APPROVED" -> state.equals("DISABLED");
              case "DISABLED" -> state.equals("APPROVED");
              default -> false;
            };
    if (!transition) throw new IllegalArgumentException("Transición de estado inválida");
    if (old.role().equals("ADMIN")
        && old.state().equals("APPROVED")
        && (!role.equals("ADMIN") || !state.equals("APPROVED"))
        && dao.query("select id from users where role='ADMIN' and state='APPROVED'").size() <= 1)
      throw new IllegalArgumentException("Debe quedar un administrador activo");
    dao.update("update users set state=?1,role=?2 where id=?3", state, role, id);
    dao.update("delete from user_module_grants where user_id=?1 and module_code='groceries'", id);
    if (!grant.equals("NONE"))
      dao.update("insert into user_module_grants values (?1,'groceries',?2)", id, grant);
  }

  public void password(UUID id, String current, String replacement) {
    identities.password(id, current, replacement);
  }

  public String issue(UUID user, String kind, String label, String cap) {
    Account a = find(user);
    if (!a.state().equals("APPROVED") || a.mustChangePassword())
      throw new IllegalArgumentException("Cuenta pendiente o cambio de contraseña requerido");
    return tokens.issue(user, kind, label, cap);
  }

  public List<Map<String, Object>> credentials(UUID user) {
    return dao.query(
        "select id,prefix,label,kind,cap,expires_at,revoked,last_used_at from credentials where user_id=?1 order by expires_at desc",
        user);
  }

  public void revoke(UUID user, UUID credential) {
    dao.update("update credentials set revoked=true where id=?1 and user_id=?2", credential, user);
  }

  public void moduleEnabled(boolean enabled) {
    dao.update("update modules set enabled=?1 where code='groceries'", enabled);
  }

  public Identity bearer(String token) {
    return tokens.resolve(token);
  }

  public Identity browser(UUID user) {
    return new Identity(find(user), null, "WEB", "READ_WRITE", "groceries");
  }

  public boolean allowed(Identity i, boolean write) {
    return permissions.allowed(i, write);
  }

  public boolean moduleEnabled() {
    return (Boolean)
        dao.query("select enabled from modules where code='groceries'").getFirst().get("enabled");
  }

  public Map<UUID, String> grants() {
    Map<UUID, String> result = new HashMap<>();
    for (var r :
        dao.query(
            "select user_id,permission from user_module_grants where module_code='groceries'"))
      result.put((UUID) r.get("user_id"), (String) r.get("permission"));
    return result;
  }

  public List<Map<String, Object>> features(Identity i) {
    if (!allowed(i, false)) return List.of();
    String permission = allowed(i, true) ? "EDIT" : "VIEW";
    return dao.query("select code,title from modules where code='groceries' and enabled").stream()
        .map(
            r ->
                Map.<String, Object>of(
                    "code",
                    r.get("code"),
                    "title",
                    r.get("title"),
                    "url",
                    "/app/" + r.get("code"),
                    "permission",
                    permission))
        .toList();
  }
}
