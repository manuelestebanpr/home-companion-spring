@org.springframework.modulith.ApplicationModule(
    id = "phone",
    allowedDependencies = {"accounts :: api", "groceries :: query", "groceries :: command"})
package com.homeserver.controllers.phone;
