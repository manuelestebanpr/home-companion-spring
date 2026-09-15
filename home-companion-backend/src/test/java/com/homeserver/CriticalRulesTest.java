package com.homeserver;

import static org.assertj.core.api.Assertions.*;

import com.homeserver.core.accounts.internal.service.PermissionService;
import com.homeserver.core.groceries.internal.shopping.ShoppingListCalculator;
import com.homeserver.core.groceries.query.ProductView;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class CriticalRulesTest {
  private ProductView product(String size, String unit, String packages, String brand) {
    return new ProductView(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Item",
        brand,
        "",
        new BigDecimal(size),
        unit,
        new BigDecimal(packages),
        0,
        null,
        "");
  }

  @Test
  void shoppingExamplesUseBaseUnitsAndRoundOnlyWholePackages() {
    var milk =
        ShoppingListCalculator.calculate(
            UUID.randomUUID(),
            "Leche",
            new BigDecimal("4"),
            "l",
            "Alpina",
            List.of(product("1", "l", "2", "Alpina"), product("1.1", "l", "1", "Colanta")));
    assertThat(milk.current()).isEqualByComparingTo("3.1");
    assertThat(milk.needed()).isEqualByComparingTo("0.9");
    assertThat(milk.packagesToBuy()).isEqualByComparingTo("1");
    var eggs =
        ShoppingListCalculator.calculate(
            UUID.randomUUID(),
            "Huevos",
            new BigDecimal("30"),
            "unit",
            null,
            List.of(product("12", "unit", "1", null)));
    assertThat(eggs.needed()).isEqualByComparingTo("18");
    assertThat(eggs.packagesToBuy()).isEqualByComparingTo("2");
    var rice =
        ShoppingListCalculator.calculate(
            UUID.randomUUID(),
            "Arroz",
            new BigDecimal("5"),
            "kg",
            null,
            List.of(product("5", "kg", "0.4", "Diana")));
    assertThat(rice.needed()).isEqualByComparingTo("3");
    assertThat(rice.packagesToBuy()).isEqualByComparingTo("1");
    var coffee =
        ShoppingListCalculator.calculate(
            UUID.randomUUID(), "Café", new BigDecimal("500"), "g", null, List.of());
    assertThat(coffee.needed()).isEqualByComparingTo("500");
    assertThat(coffee.noProductKnown()).isTrue();
  }

  @Test
  void keysNeverInheritAdministratorPowerOrExceedOwnerGrant() {
    for (String role : List.of("MEMBER", "ADMIN"))
      for (String grant : List.of("NONE", "VIEW", "EDIT"))
        for (String cap : List.of("READ", "READ_WRITE")) {
          assertThat(PermissionService.evaluate("KEY", role, grant, cap, "groceries", true))
              .isEqualTo(grant.equals("EDIT") && cap.equals("READ_WRITE"));
          assertThat(PermissionService.evaluate("KEY", role, grant, cap, "groceries", false))
              .isEqualTo(!grant.equals("NONE"));
          assertThat(PermissionService.evaluate("KEY", role, grant, cap, "other", false)).isFalse();
        }
    assertThat(PermissionService.evaluate("WEB", "ADMIN", "NONE", "READ_WRITE", "groceries", true))
        .isTrue();
  }
}
