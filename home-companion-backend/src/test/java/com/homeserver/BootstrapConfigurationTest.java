package com.homeserver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.homeserver.core.accounts.internal.Bootstrap;
import com.homeserver.core.accounts.internal.dao.AccountDao;
import com.homeserver.core.accounts.internal.service.IdentityService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BootstrapConfigurationTest {
  @Test
  void startupRequiresAnExplicitNonblankPasswordEvenBeforeCheckingExistingAccounts() {
    AccountDao dao = mock(AccountDao.class);
    var context =
        new ApplicationContextRunner()
            .withBean(AccountDao.class, () -> dao)
            .withBean(IdentityService.class, () -> mock(IdentityService.class))
            .withBean(Bootstrap.class)
            .withPropertyValues("home.admin-username=admin");
    context.run(
        c ->
            assertThat(c.getStartupFailure())
                .hasRootCauseMessage(
                    "Required property home.admin-password (ADMIN_PASSWORD) is missing or blank"));
    context
        .withPropertyValues("home.admin-password= ")
        .run(
            c ->
                assertThat(c.getStartupFailure())
                    .hasRootCauseMessage(
                        "Required property home.admin-password (ADMIN_PASSWORD) is missing or blank"));
    context
        .withPropertyValues("home.admin-password=short")
        .run(
            c ->
                assertThat(c.getStartupFailure())
                    .hasRootCauseMessage(
                        "home.admin-password (ADMIN_PASSWORD) must contain at least 12 characters and at most 72 UTF-8 bytes"));
    context
        .withPropertyValues("home.admin-password=ChosenPassword123!")
        .run(c -> assertThat(c).hasNotFailed());
    verifyNoInteractions(dao);
  }
}
