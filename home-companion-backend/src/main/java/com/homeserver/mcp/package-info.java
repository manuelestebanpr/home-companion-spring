@org.springframework.modulith.ApplicationModule(
    id = "mcp",
    allowedDependencies = {"groceries :: query", "groceries :: command"})
package com.homeserver.mcp;
