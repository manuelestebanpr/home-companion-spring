package com.homeserver;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Loads local configuration without exporting secrets or changing process environment. */
public final class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    load(environment, Path.of("."));
  }

  static void load(ConfigurableEnvironment environment, Path directory) {
    Map<String, Object> values = new HashMap<>();
    try {
      Dotenv.configure()
          .directory(directory.toAbsolutePath().toString())
          .ignoreIfMissing()
          .load()
          .entries(Dotenv.Filter.DECLARED_IN_ENV_FILE)
          .forEach(
              entry -> {
                String value = entry.getValue();
                // dotenv-java strips double quotes; also accept Compose's literal single quotes.
                if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                  value = value.substring(1, value.length() - 1);
                }
                values.put(entry.getKey(), value);
              });
    } catch (DotenvException e) {
      // Parser exceptions may contain a secret-bearing line; never attach them to startup logs.
      throw new IllegalStateException("Cannot read .env; check file access and KEY=value syntax");
    }
    var source = new SystemEnvironmentPropertySource("homeCompanionDotenv", values);
    environment
        .getPropertySources()
        .addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
  }

  @Override
  public int getOrder() {
    return ConfigDataEnvironmentPostProcessor.ORDER - 1;
  }
}
