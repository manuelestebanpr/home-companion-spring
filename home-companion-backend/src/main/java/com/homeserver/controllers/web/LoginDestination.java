package com.homeserver.controllers.web;

import jakarta.servlet.http.HttpServletRequest;

/** Only known local destinations can be resumed after authentication. */
final class LoginDestination {
  private static final String ATTRIBUTE = "HC_LOGIN_DESTINATION";

  private LoginDestination() {}

  static String safe(String destination) {
    return "/admin".equals(destination) ? "/admin" : "/app";
  }

  static void remember(HttpServletRequest request, String destination) {
    request.getSession().setAttribute(ATTRIBUTE, safe(destination));
  }

  static String complete(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session == null) return "/app";
    String destination = safe((String) session.getAttribute(ATTRIBUTE));
    session.removeAttribute(ATTRIBUTE);
    return destination;
  }
}
