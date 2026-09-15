package com.homeserver.security;

import com.homeserver.core.accounts.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  private final AccessFacade accounts;
  private final RequestAccess access;
  private final String origin;

  public SecurityConfig(
      AccessFacade accounts, RequestAccess access, @Value("${home.origin}") String origin) {
    this.accounts = accounts;
    this.access = access;
    this.origin = origin;
  }

  @Bean
  @Order(1)
  SecurityFilterChain api(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**", "/mcp", "/mcp/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
        .requestCache(RequestCacheConfigurer::disable)
        .addFilterBefore(new ThrottleFilter(), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new CredentialFilter(accounts, true, origin),
            UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/v1/auth/login", "/api/v1/auth/register")
                    .permitAll()
                    .requestMatchers("/api/v1/me", "/api/v1/auth/logout")
                    .authenticated()
                    .requestMatchers("/api/v1/groceries/**")
                    .access(
                        (auth, ctx) ->
                            new AuthorizationDecision(
                                access.groceries(
                                    auth.get(), !ctx.getRequest().getMethod().equals("GET"))))
                    .requestMatchers("/api/v1/features", "/mcp")
                    .access((auth, ctx) -> new AuthorizationDecision(access.ready(auth.get())))
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, ex) -> problem(res, 401, "invalid-token"))
                    .accessDeniedHandler(
                        (req, res, ex) -> problem(res, 403, "insufficient-permission")));
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain web(HttpSecurity http) throws Exception {
    http.securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
        .requestCache(RequestCacheConfigurer::disable)
        .addFilterBefore(new ThrottleFilter(), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new CredentialFilter(accounts, false, origin),
            UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/",
                        "/app/login",
                        "/admin/login",
                        "/app/register",
                        "/assets/**",
                        "/error",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers(
                        "/admin/**", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**")
                    .access((auth, ctx) -> new AuthorizationDecision(access.admin(auth.get())))
                    .requestMatchers("/app/groceries/**")
                    .access(
                        (auth, ctx) ->
                            new AuthorizationDecision(
                                access.groceries(
                                    auth.get(), !ctx.getRequest().getMethod().equals("GET"))))
                    .requestMatchers("/app/keys/**")
                    .access((auth, ctx) -> new AuthorizationDecision(access.ready(auth.get())))
                    .requestMatchers("/app/**", "/app")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                    (req, res, ex) ->
                        res.sendRedirect(
                            req.getRequestURI().equals("/admin")
                                    || req.getRequestURI().startsWith("/admin/")
                                ? "/admin/login"
                                : "/app/login")))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
        .logout(l -> l.disable());
    return http.build();
  }

  private static void problem(jakarta.servlet.http.HttpServletResponse res, int status, String type)
      throws java.io.IOException {
    res.setStatus(status);
    res.setContentType("application/problem+json");
    if (status == 401) res.setHeader("WWW-Authenticate", "Bearer realm=\"home-companion\"");
    res.getWriter()
        .write(
            "{\"type\":\"/problems/"
                + type
                + "\",\"title\":\""
                + type
                + "\",\"status\":"
                + status
                + "}");
  }
}
