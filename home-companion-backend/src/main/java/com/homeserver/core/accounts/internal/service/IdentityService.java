package com.homeserver.core.accounts.internal.service;

import com.homeserver.core.accounts.api.Account;
import com.homeserver.core.accounts.internal.dao.AccountDao;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
  private final AccountDao dao;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  public IdentityService(AccountDao dao) {
    this.dao = dao;
  }

  public Account map(Map<String, Object> r) {
    return new Account(
        (UUID) r.get("id"),
        (String) r.get("username"),
        (String) r.get("state"),
        (String) r.get("role"),
        (Boolean) r.get("must_change_password"));
  }

  public Account find(UUID id) {
    return dao.query("select * from users where id=?1", id).stream()
        .findFirst()
        .map(this::map)
        .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));
  }

  public Account login(String username, String password) {
    var rows = dao.query("select * from users where username=?1", normalize(username));
    // A dummy hash makes unknown usernames incur the same BCrypt work.
    String hash =
        rows.isEmpty()
            ? "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW"
            : (String) rows.getFirst().get("password_hash");
    if (password == null
        || password.length() > 72
        || !encoder.matches(password, hash)
        || rows.isEmpty()) throw new IllegalArgumentException("Credenciales incorrectas");
    return map(rows.getFirst());
  }

  public Account create(
      String username, String password, String state, String role, boolean change) {
    validatePassword(password);
    UUID id = UUID.randomUUID();
    dao.update(
        "insert into users values (?1,?2,?3,?4,?5,?6)",
        id,
        normalize(username),
        encoder.encode(password),
        state,
        role,
        change);
    return find(id);
  }

  public void password(UUID id, String current, String replacement) {
    login(find(id).username(), current);
    validatePassword(replacement);
    dao.update(
        "update users set password_hash=?1,must_change_password=false where id=?2",
        encoder.encode(replacement),
        id);
    dao.update("update credentials set revoked=true where user_id=?1", id);
  }

  private String normalize(String name) {
    if (name == null || !name.trim().matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalArgumentException("Usuario: 3–60 letras, números, puntos o guiones");
    return name.trim().toLowerCase(Locale.ROOT);
  }

  private void validatePassword(String p) {
    if (p == null
        || p.length() < 12
        || p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("Contraseña: mínimo 12 caracteres y máximo 72 bytes");
  }
}
