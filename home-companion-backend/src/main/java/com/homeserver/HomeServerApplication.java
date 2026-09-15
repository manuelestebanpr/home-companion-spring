package com.homeserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(
    exclude =
        org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
public class HomeServerApplication {
  public static void main(String[] args) {
    SpringApplication.run(HomeServerApplication.class, args);
  }
}
