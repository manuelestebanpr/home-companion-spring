package com.homeserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class DotenvEnvironmentPostProcessorTest {
  @TempDir Path directory;

  @Test
  void loadsLiteralSecretsAndRelaxedPropertiesWhileExternalEnvironmentWins() throws Exception {
    Files.writeString(
        directory.resolve(".env"),
        "ADMIN_PASSWORD='Chosen # $ password'\nSERVER_PORT=8181\nDB_USER=file_user\n");
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("DB_USER", "external_user")));
    DotenvEnvironmentPostProcessor.load(environment, directory);
    assertThat(environment.getProperty("ADMIN_PASSWORD")).isEqualTo("Chosen # $ password");
    assertThat(environment.getProperty("server.port")).isEqualTo("8181");
    assertThat(environment.getProperty("DB_USER")).isEqualTo("external_user");
  }

  @Test
  void missingFileIsAllowedButMalformedFileFailsWithoutLeakingContents() throws Exception {
    var environment = new StandardEnvironment();
    DotenvEnvironmentPostProcessor.load(environment, directory);
    Files.writeString(directory.resolve(".env"), "secret without an assignment\n");
    assertThatThrownBy(() -> DotenvEnvironmentPostProcessor.load(environment, directory))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot read .env; check file access and KEY=value syntax")
        .hasNoCause();
  }
}
