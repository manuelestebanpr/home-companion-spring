package com.homeserver.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.filter.OncePerRequestFilter;

final class ThrottleFilter extends OncePerRequestFilter {
  private record Window(long minute, int count) {}

  private final Map<String, Window> windows = new HashMap<>();

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();
    if (req.getMethod().equals("POST") && (path.endsWith("/login") || path.endsWith("/register"))) {
      boolean denied;
      synchronized (windows) {
        long now = System.currentTimeMillis() / 60000;
        windows.entrySet().removeIf(e -> e.getValue().minute() < now);
        String ip = req.getRemoteAddr();
        Window old = windows.get(ip);
        denied = windows.size() > 10000 || old != null && old.count() >= 10;
        if (!denied) windows.put(ip, new Window(now, old == null ? 1 : old.count() + 1));
      }
      if (denied) {
        res.setStatus(429);
        res.setHeader("Retry-After", "60");
        res.setContentType("application/problem+json");
        res.getWriter().write("{\"title\":\"Demasiados intentos\",\"status\":429}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
