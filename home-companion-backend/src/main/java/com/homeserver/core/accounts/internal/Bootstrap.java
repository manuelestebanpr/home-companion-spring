package com.homeserver.core.accounts.internal;

import com.homeserver.core.accounts.internal.dao.AccountDao;
import com.homeserver.core.accounts.internal.service.IdentityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {
  private final AccountDao dao;
  private final IdentityService identities;
  private final String username, password;

  public Bootstrap(
      AccountDao dao,
      IdentityService identities,
      @Value("${home.admin-username}") String username,
      @Value("${home.admin-password:}") String password) {
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException(
          "Required property home.admin-password (ADMIN_PASSWORD) is missing or blank");
    }
    if (password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException(
          "home.admin-password (ADMIN_PASSWORD) must contain at least 12 characters and at most 72 UTF-8 bytes");
    }
    this.dao = dao;
    this.identities = identities;
    this.username = username;
    this.password = password;
  }

  @Transactional
  public void run(ApplicationArguments args) {
    if (dao.query("select id from users").isEmpty()) {
      identities.create(username, password, "APPROVED", "ADMIN", false);
    }
    dao.update("update users set must_change_password=false where role='ADMIN'");
    dao.update(
        """
        insert into user_module_grants (user_id, module_code, permission)
        select u.id, m.code, 'EDIT' from users u cross join modules m where u.role='ADMIN'
        on conflict (user_id, module_code) do update set permission='EDIT'
        """);
  }
}
