@org.springframework.modulith.ApplicationModule(
    id = "web",
    allowedDependencies = {"accounts :: api", "groceries :: query", "groceries :: command"})
package com.homeserver.controllers.web;
