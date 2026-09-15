package com.homeserver.security;

import com.homeserver.core.accounts.api.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class CredentialFilter extends OncePerRequestFilter {
  private final AccessFacade access;
  private final boolean bearer;
  private final String origin;

  CredentialFilter(AccessFacade access, boolean bearer, String origin) {
    this.access = access;
    this.bearer = bearer;
    this.origin = origin;
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    Identity identity = null;
    if (bearer) {
      String supplied = req.getHeader("Origin");
      if (supplied != null && !supplied.equals(origin)) {
        res.sendError(403);
        return;
      }
      String header = req.getHeader("Authorization");
      if (header != null && header.startsWith("Bearer "))
        identity = access.bearer(header.substring(7));
      if (req.getRequestURI().equals("/mcp") && identity != null && !identity.kind().equals("KEY"))
        identity = null;
    } else {
      if (req.getHeader("Authorization") != null) {
        res.sendError(401);
        return;
      }
      HttpSession session = req.getSession(false);
      if (session != null && session.getAttribute("HC_USER") instanceof UUID id) {
        try {
          identity = access.browser(id);
        } catch (IllegalArgumentException ignored) {
          session.invalidate();
        }
      }
    }
    var context = SecurityContextHolder.createEmptyContext();
    if (identity != null)
      context.setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of()));
    SecurityContextHolder.setContext(context);
    try {
      chain.doFilter(req, res);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
