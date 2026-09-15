package com.homeserver.controllers.phone;

import java.net.URI;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages = "com.homeserver.controllers.phone")
public class ApiErrors {
  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
  ProblemDetail invalid(Exception e) {
    var p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            e instanceof IllegalArgumentException ? e.getMessage() : "JSON inválido");
    p.setType(URI.create("/problems/validation-failed"));
    return p;
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail conflict() {
    var p =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "El registro ya existe o está en uso");
    p.setType(URI.create("/problems/entity-conflict"));
    return p;
  }
}
