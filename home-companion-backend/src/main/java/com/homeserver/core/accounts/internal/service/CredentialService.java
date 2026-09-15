package com.homeserver.core.accounts.internal.service;

import com.homeserver.core.accounts.api.*;
import com.homeserver.core.accounts.internal.dao.AccountDao;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CredentialService {
  private final AccountDao dao;
  private final IdentityService identities;

  public CredentialService(AccountDao dao, IdentityService identities) {
    this.dao = dao;
    this.identities = identities;
  }

  public String issue(UUID user, String kind, String label, String cap) {
    if (!Set.of("KEY", "SESSION").contains(kind)
        || !Set.of("READ", "READ_WRITE").contains(cap)
        || label == null
        || label.isBlank()
        || label.length() > 80) throw new IllegalArgumentException("Credencial inválida");
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token =
        (kind.equals("KEY") ? "hs_key_" : "hs_sess_")
            + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    dao.update(
        "insert into credentials(id,user_id,token_hash,prefix,kind,label,cap,module_code,expires_at) values (?1,?2,?3,?4,?5,?6,?7,'groceries',CURRENT_TIMESTAMP + cast(?8 as interval))",
        UUID.randomUUID(),
        user,
        hash(token),
        token.substring(0, 16),
        kind,
        label,
        cap,
        kind.equals("KEY") ? "365 days" : "90 days");
    return token;
  }

  public Identity resolve(String token) {
    if (token == null
        || token.length() > 100
        || !(token.startsWith("hs_key_") || token.startsWith("hs_sess_"))) return null;
    var rows =
        dao.query(
            "select * from credentials where token_hash=?1 and not revoked and expires_at>CURRENT_TIMESTAMP",
            hash(token));
    if (rows.isEmpty()) return null;
    var row = rows.getFirst();
    if (!MessageDigest.isEqual(
        hash(token).getBytes(StandardCharsets.UTF_8),
        ((String) row.get("token_hash")).getBytes(StandardCharsets.UTF_8))) return null;
    UUID id = (UUID) row.get("id");
    String kind = (String) row.get("kind");
    dao.update("update credentials set last_used_at=CURRENT_TIMESTAMP where id=?1", id);
    if (kind.equals("SESSION"))
      dao.update(
          "update credentials set expires_at=CURRENT_TIMESTAMP+interval '90 days' where id=?1", id);
    return new Identity(
        identities.find((UUID) row.get("user_id")),
        id,
        kind,
        (String) row.get("cap"),
        (String) row.get("module_code"));
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
