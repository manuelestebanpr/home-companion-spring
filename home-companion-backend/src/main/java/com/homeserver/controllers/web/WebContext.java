package com.homeserver.controllers.web;

import com.homeserver.core.accounts.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(basePackages = "com.homeserver.controllers.web")
public class WebContext {
  private final AccessFacade access;

  public WebContext(AccessFacade access) {
    this.access = access;
  }

  @ModelAttribute
  void context(Model model, Authentication auth, jakarta.servlet.http.HttpServletRequest request) {
    model.addAttribute("currentPath", request.getRequestURI());
    if (auth != null && auth.getPrincipal() instanceof Identity i) {
      model.addAttribute("account", i.account());
      model.addAttribute("canEdit", access.allowed(i, true));
      model.addAttribute("features", access.features(i));
      model.addAttribute(
          "admin",
          i.account().role().equals("ADMIN")
              && i.account().state().equals("APPROVED")
              && !i.account().mustChangePassword());
    }
  }

  @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
  ModelAndView invalid(Exception e) {
    var view = new ModelAndView("error");
    view.setStatus(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
    view.addObject(
        "message",
        e instanceof DataIntegrityViolationException
            ? "El registro ya existe o está en uso."
            : e.getMessage());
    return view;
  }
}
