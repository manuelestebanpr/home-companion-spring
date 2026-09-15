package com.homeserver;

import static org.assertj.core.api.Assertions.*;

import com.homeserver.core.accounts.api.*;
import com.homeserver.core.groceries.command.*;
import com.homeserver.core.groceries.query.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "home.admin-password=InitialPassword123!",
      "server.servlet.session.cookie.secure=false"
    })
class CriticalFlowIT {
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.3");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    postgres.start();
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
  }

  @AfterAll
  static void stopDatabase() {
    postgres.stop();
  }

  @LocalServerPort int port;
  @Autowired AccountFacade accounts;
  @Autowired com.homeserver.core.accounts.internal.Bootstrap bootstrap;
  @Autowired com.homeserver.core.accounts.internal.dao.AccountDao accountDao;
  @Autowired GroceryWriteFacade writes;
  @Autowired GroceryReadFacade reads;
  @Autowired JsonMapper json;
  final HttpClient client =
      HttpClient.newBuilder()
          .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
          .build();

  HttpResponse<String> request(String method, String path, String body, String bearer, String type)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", type)
            .header("Accept", "application/json, text/event-stream");
    if (bearer != null) b.header("Authorization", "Bearer " + bearer);
    return client.send(
        b.method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  String csrf(String path) throws Exception {
    var r = request("GET", path, null, null, "text/html");
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    var matcher =
        java.util.regex.Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(r.body());
    assertThat(matcher.find()).as(r.body()).isTrue();
    return matcher.group(1);
  }

  HttpResponse<String> form(String path, String fields, String csrf) throws Exception {
    return request(
        "POST",
        path,
        fields + "&_csrf=" + URLEncoder.encode(csrf, java.nio.charset.StandardCharsets.UTF_8),
        null,
        "application/x-www-form-urlencoded");
  }

  HttpResponse<String> mcp(String key, String method, String params) throws Exception {
    return request(
        "POST",
        "/mcp",
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}",
        key,
        "application/json");
  }

  @Test
  void authenticatedWebRestAndMcpEnforceLiveGrantsAndRetrySafety() throws Exception {
    assertThat(
            request("GET", "/api/v1/groceries/stock", null, null, "application/json").statusCode())
        .isEqualTo(401);
    assertThat(
            request("GET", "/app", null, null, "text/html")
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/app/login");
    assertThat(
            request("GET", "/admin", null, null, "text/html")
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/admin/login");
    var webLogin = request("GET", "/app/login", null, null, "text/html");
    assertThat(webLogin.statusCode()).isEqualTo(200);
    assertThat(webLogin.body()).contains("Acceso de administración", "/assets/app.css");
    var adminLogin = request("GET", "/admin/login", null, null, "text/html");
    assertThat(adminLogin.statusCode()).isEqualTo(200);
    assertThat(adminLogin.body())
        .contains("Entrar a administración", "name=\"destination\" value=\"/admin\"");
    assertThat(request("GET", "/assets/app.css", null, null, "text/css").statusCode())
        .isEqualTo(200);
    assertThat(
            form(
                    "/app/login",
                    "username=admin&password=wrong&destination=/admin",
                    csrf("/admin/login"))
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/admin/login?error");
    var signedIn =
        form(
            "/app/login",
            "username=admin&password=InitialPassword123%21&destination=/admin",
            csrf("/admin/login"));
    assertThat(signedIn.statusCode()).isEqualTo(302);
    assertThat(
            signedIn
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/admin");
    for (String page :
        List.of("/admin", "/app/groceries", "/app/groceries/shopping", "/app/groceries/targets")) {
      assertThat(request("GET", page, null, null, "text/html").statusCode())
          .as(page)
          .isEqualTo(200);
    }
    var pending = accounts.register("pending-user", "PendingPassword123!");
    assertThat(pending.state()).isEqualTo("PENDING");
    assertThat(accounts.grants()).doesNotContainKey(pending.id());
    var usersPage = request("GET", "/app/keys", null, null, "text/html");
    assertThat(usersPage.body()).contains("pending-user", "Usuarios y acceso a módulos");
    assertThat(
            form(
                    "/admin/users",
                    "username=added-user&password=AddedPassword123%21",
                    csrf("/app/keys"))
                .statusCode())
        .isEqualTo(302);
    var addedUser =
        accounts.users().stream()
            .filter(u -> u.username().equals("added-user"))
            .findFirst()
            .orElseThrow();
    assertThat(addedUser.state()).isEqualTo("PENDING");
    assertThat(accounts.grants()).doesNotContainKey(addedUser.id());
    var approved =
        form(
            "/admin/users/" + pending.id(),
            "state=APPROVED&role=MEMBER&grant=VIEW&destination=/app/keys",
            csrf("/app/keys"));
    assertThat(approved.statusCode()).isEqualTo(302);
    assertThat(
            approved
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/app/keys");
    assertThat(accounts.find(pending.id()).state()).isEqualTo("APPROVED");
    assertThat(accounts.grants()).containsEntry(pending.id(), "VIEW");
    var changed =
        form(
            "/app/password",
            "current=InitialPassword123%21&replacement=ReplacementPassword123%21",
            csrf("/app/password"));
    assertThat(changed.statusCode()).isEqualTo(302);
    assertThat(
            changed
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/app");
    for (String page :
        List.of(
            "/app",
            "/admin",
            "/app/keys",
            "/app/groceries",
            "/app/groceries/new",
            "/app/groceries/shopping",
            "/app/groceries/targets"))
      assertThat(request("GET", page, null, null, "text/html").statusCode())
          .as(page)
          .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/app/keys",
                    "label=bad&cap=READ",
                    null,
                    "application/x-www-form-urlencoded")
                .statusCode())
        .isEqualTo(403);
    Account admin = accounts.login("admin", "ReplacementPassword123!");
    String read = accounts.issue(admin.id(), "KEY", "read test", "READ");
    String edit = accounts.issue(admin.id(), "KEY", "edit test", "READ_WRITE");
    var login =
        request(
            "POST",
            "/api/v1/auth/login",
            "{\"username\":\"admin\",\"password\":\"ReplacementPassword123!\"}",
            null,
            "application/json");
    assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
    String phone = json.readTree(login.body()).path("token").asText();
    assertThat(mcp(phone, "tools/list", "{}").statusCode()).isEqualTo(401);
    var initialize =
        mcp(
            edit,
            "initialize",
            "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"critical-test\",\"version\":\"1\"}}");
    assertThat(initialize.statusCode()).as(initialize.body()).isEqualTo(200);
    var tools = mcp(edit, "tools/list", "{}");
    assertThat(tools.statusCode()).as(tools.body()).isEqualTo(200);
    assertThat(tools.body())
        .contains(
            "groceries_add_stock",
            "groceries_list_desired",
            "groceries_shopping_list",
            "groceries_list_stock");
    String addition =
        "{\"name\":\"groceries_add_stock\",\"arguments\":{\"new_product\":{\"item\":\"Leche\",\"kind\":\"VOLUME\",\"brand\":\"Alpina\",\"attributes\":[\"Deslactosada\"],\"packageQuantity\":1,\"unit\":\"l\"},\"packages\":2,\"request_id\":\"first-milk\"}}";
    assertThat(mcp(read, "tools/call", addition).body()).contains("\"isError\":true");
    assertThat(reads.stock("Leche", 50, 0)).isEmpty();
    var added = mcp(edit, "tools/call", addition);
    assertThat(added.statusCode()).as(added.body()).isEqualTo(200);
    assertThat(added.body()).contains("\"isError\":false");
    assertThat(mcp(edit, "tools/call", addition).body()).contains("\"isError\":false");
    var milk = reads.stock("Leche", 50, 0).getFirst();
    assertThat(milk.packages()).isEqualByComparingTo("2");
    assertThat(mcp(edit, "tools/call", addition.replace("\"packages\":2", "\"packages\":3")).body())
        .contains("\"isError\":true");
    writes.target(milk.itemId(), new BigDecimal("4"), "l", "Alpina");
    var rest =
        json.readTree(
            request("GET", "/api/v1/groceries/shopping", null, read, "application/json").body());
    var rpc =
        json.readTree(
            mcp(read, "tools/call", "{\"name\":\"groceries_shopping_list\",\"arguments\":{}}")
                .body());
    assertThat(rpc.path("result").path("structuredContent")).isEqualTo(rest);
    assertThat(
            request(
                    "POST",
                    "/api/v1/groceries/stock/{id}/adjust".replace("{id}", milk.id().toString()),
                    "{\"delta\":-5}",
                    edit,
                    "application/json")
                .statusCode())
        .isEqualTo(422);
    assertThat(request("GET", "/app/groceries", null, null, "text/html").statusCode())
        .isEqualTo(200);
    assertThat(request("GET", "/app/groceries/shopping", null, null, "text/html").statusCode())
        .isEqualTo(200);
    assertThat(request("GET", "/app/groceries/targets", null, null, "text/html").statusCode())
        .isEqualTo(200);
    accounts.update(admin.id(), "APPROVED", "ADMIN", "VIEW");
    assertThat(mcp(edit, "tools/call", addition).body()).contains("\"isError\":true");
    accounts.moduleEnabled(false);
    assertThat(
            mcp(read, "tools/call", "{\"name\":\"groceries_list_stock\",\"arguments\":{}}").body())
        .contains("\"isError\":true");
    accounts.moduleEnabled(true);
    var credential =
        accounts.credentials(admin.id()).stream()
            .filter(k -> k.get("label").equals("edit test"))
            .findFirst()
            .orElseThrow();
    accounts.revoke(admin.id(), (UUID) credential.get("id"));
    assertThat(mcp(edit, "tools/list", "{}").statusCode()).isEqualTo(401);
    assertThat(request("GET", "/admin", null, read, "text/html").statusCode()).isEqualTo(401);
    assertThatThrownBy(() -> accounts.update(admin.id(), "DISABLED", "MEMBER", "NONE"))
        .isInstanceOf(IllegalArgumentException.class);
    form("/app/logout", "", csrf("/app"));
    assertThat(
            form(
                    "/app/login",
                    "username=admin&password=ReplacementPassword123%21",
                    csrf("/app/login"))
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/app");
    form("/app/logout", "", csrf("/app"));
    assertThat(
            form(
                    "/app/login",
                    "username=admin&password=ReplacementPassword123%21&destination=https://example.com",
                    csrf("/app/login"))
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/app");
    form("/app/logout", "", csrf("/app"));
    assertThat(
            form(
                    "/app/login",
                    "username=admin&password=ReplacementPassword123%21&destination=/admin",
                    csrf("/admin/login"))
                .headers()
                .firstValue("Location")
                .map(value -> value.replace("http://localhost:" + port, "")))
        .contains("/admin");
    assertThat(request("GET", "/admin", null, null, "text/html").statusCode()).isEqualTo(200);
    form("/app/logout", "", csrf("/app"));
    var member = accounts.register("member", "MemberPassword123!");
    accounts.update(member.id(), "APPROVED", "MEMBER", "VIEW");
    form(
        "/app/login",
        "username=member&password=MemberPassword123%21&destination=/admin",
        csrf("/admin/login"));
    assertThat(request("GET", "/admin", null, null, "text/html").statusCode()).isEqualTo(403);
    assertThat(request("GET", "/app/keys", null, null, "text/html").body())
        .doesNotContain("pending-user", "Usuarios y acceso a módulos");
    assertThat(
            form(
                    "/admin/users",
                    "username=forbidden&password=ForbiddenPassword123%21",
                    csrf("/app/keys"))
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  void bootstrapRepairsExistingAdminsAndGrantsEveryModuleWithoutChangingPasswords() {
    var admin =
        accounts.users().stream().filter(u -> u.role().equals("ADMIN")).findFirst().orElseThrow();
    var member = accounts.register("bootstrap-member", "MemberPassword123!");
    accountDao.update("insert into modules values ('test-module','Test module',true)");
    accountDao.update("update users set must_change_password=true where id=?1", admin.id());
    accountDao.update("delete from user_module_grants where user_id=?1", admin.id());
    var passwordBefore =
        accountDao.query("select password_hash from users where id=?1", admin.id());
    bootstrap.run(null);
    bootstrap.run(null);
    assertThat(accounts.find(admin.id()).mustChangePassword()).isFalse();
    assertThat(accountDao.query("select password_hash from users where id=?1", admin.id()))
        .isEqualTo(passwordBefore);
    var grants =
        accountDao.query(
            "select module_code,permission from user_module_grants where user_id=?1 order by module_code",
            admin.id());
    assertThat(grants)
        .containsExactly(
            Map.of("module_code", "groceries", "permission", "EDIT"),
            Map.of("module_code", "test-module", "permission", "EDIT"));
    assertThat(accountDao.query("select * from user_module_grants where user_id=?1", member.id()))
        .isEmpty();
  }

  @Test
  void concurrentIncrementsAndFailedQuickAddAreAtomic() throws Exception {
    var product =
        new AddStock.NewProduct(
            "Arroz", "MASS", "General", "Diana", List.of(), new BigDecimal("5"), "kg");
    var row = writes.add(new AddStock(null, product, BigDecimal.ONE), "test", "rice-start");
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new ArrayList<Future<?>>();
      for (int i = 0; i < 12; i++)
        calls.add(
            executor.submit(
                () ->
                    writes.add(
                        new AddStock(row.id(), null, BigDecimal.ONE), "test", "same-retry")));
      for (var call : calls) call.get();
    }
    assertThat(reads.stock("Arroz", 50, 0).getFirst().packages()).isEqualByComparingTo("2");
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var calls = new ArrayList<Future<?>>();
      for (int n = 0; n < 12; n++)
        calls.add(executor.submit(() -> writes.adjust(row.id(), BigDecimal.ONE)));
      for (var call : calls) call.get();
    }
    assertThat(reads.stock("Arroz", 50, 0).getFirst().packages()).isEqualByComparingTo("14");
    var invalid =
        new AddStock.NewProduct(
            "Should Roll Back",
            "MASS",
            "General",
            "New Brand",
            java.util.stream.IntStream.range(0, 6).mapToObj(n -> "a".repeat(79) + n).toList(),
            BigDecimal.ONE,
            "g");
    assertThatThrownBy(() -> writes.add(new AddStock(null, invalid, BigDecimal.ONE), "test", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(reads.items().stream().noneMatch(i -> i.name().equals("Should Roll Back"))).isTrue();
  }
}
