package com.homeserver;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleArchitectureTest {
  @Test
  void sixModulesHaveNoCyclesOrInternalDependencies() {
    System.setProperty("spring.modulith.detection-strategy", "explicitly-annotated");
    var modules = ApplicationModules.of(HomeServerApplication.class);
    assertThat(modules.stream().map(m -> m.getIdentifier().toString()))
        .containsExactlyInAnyOrder("web", "phone", "security", "mcp", "accounts", "groceries");
    modules.verify();
  }
}
